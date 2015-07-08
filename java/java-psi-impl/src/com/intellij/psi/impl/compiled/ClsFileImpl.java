/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.compiled;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.compiled.ClassFileDecompilers;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.stubs.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.cls.ClsFormatException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.reference.SoftReference.dereference;

public class ClsFileImpl extends ClsRepositoryPsiElement<PsiClassHolderFileStub>
                         implements PsiJavaFile, PsiFileWithStubSupport, PsiFileEx, Queryable, PsiClassOwnerEx, PsiCompiledFile {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsFileImpl");

  private static final String BANNER =
    "\n" +
    "  // IntelliJ API Decompiler stub source generated from a class file\n" +
    "  // Implementation of methods is not available\n" +
    "\n";

  private static final Key<Document> CLS_DOCUMENT_LINK_KEY = Key.create("cls.document.link");

  /** NOTE: you absolutely MUST NOT hold PsiLock under the mirror lock */
  private final Object myMirrorLock = new Object();
  private final Object myStubLock = new Object();

  private final FileViewProvider myViewProvider;
  private final boolean myIsForDecompiling;
  private volatile SoftReference<StubTree> myStub;
  private volatile TreeElement myMirrorFileElement;
  private volatile ClsPackageStatementImpl myPackageStatement = null;
  private volatile LanguageLevel myLanguageLevel = null;
  private boolean myIsPhysical = true;

  public ClsFileImpl(@NotNull FileViewProvider viewProvider) {
    this(viewProvider, false);
  }

  private ClsFileImpl(@NotNull FileViewProvider viewProvider, boolean forDecompiling) {
    super(null);
    myViewProvider = viewProvider;
    myIsForDecompiling = forDecompiling;
    JavaElementType.CLASS.getIndex();  // initialize Java stubs
  }

  @Override
  public PsiManager getManager() {
    return myViewProvider.getManager();
  }

  @Override
  @NotNull
  public VirtualFile getVirtualFile() {
    return myViewProvider.getVirtualFile();
  }

  @Override
  public boolean processChildren(final PsiElementProcessor<PsiFileSystemItem> processor) {
    return true;
  }

  @Override
  public PsiDirectory getParent() {
    return getContainingDirectory();
  }

  @Override
  public PsiDirectory getContainingDirectory() {
    VirtualFile parentFile = getVirtualFile().getParent();
    if (parentFile == null) return null;
    return getManager().findDirectory(parentFile);
  }

  @Override
  public PsiFile getContainingFile() {
    if (!isValid()) throw new PsiInvalidElementAccessException(this);
    return this;
  }

  @Override
  public boolean isValid() {
    return myIsForDecompiling || getVirtualFile().isValid();
  }

  protected boolean isForDecompiling() {
    return myIsForDecompiling;
  }

  @Override
  @NotNull
  public String getName() {
    return getVirtualFile().getName();
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    return getClasses(); // TODO : package statement?
  }

  @Override
  @NotNull
  public PsiClass[] getClasses() {
    return getStub().getClasses();
  }

  @Override
  public PsiPackageStatement getPackageStatement() {
    getStub(); // Make sure myPackageStatement initializes.

    ClsPackageStatementImpl statement = myPackageStatement;
    if (statement == null) statement = new ClsPackageStatementImpl(this);
    return statement.getPackageName() != null ? statement : null;
  }

  @Override
  @NotNull
  public String getPackageName() {
    PsiPackageStatement statement = getPackageStatement();
    return statement == null ? "" : statement.getPackageName();
  }

  @Override
  public void setPackageName(final String packageName) throws IncorrectOperationException {
    throw new IncorrectOperationException("Cannot set package name for compiled files");
  }

  @Override
  public PsiImportList getImportList() {
    return null;
  }

  @Override
  public boolean importClass(PsiClass aClass) {
    throw new UnsupportedOperationException("Cannot add imports to compiled classes");
  }

  @Override
  @NotNull
  public PsiElement[] getOnDemandImports(boolean includeImplicit, boolean checkIncludes) {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiClass[] getSingleClassImports(boolean checkIncludes) {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public String[] getImplicitlyImportedPackages() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public Set<String> getClassNames() {
    return Collections.singleton(getVirtualFile().getNameWithoutExtension());
  }

  @Override
  @NotNull
  public PsiJavaCodeReferenceElement[] getImplicitlyImportedPackageReferences() {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  @Override
  public PsiJavaCodeReferenceElement findImportReferenceTo(PsiClass aClass) {
    return null;
  }

  @Override
  @NotNull
  public LanguageLevel getLanguageLevel() {
    LanguageLevel level = myLanguageLevel;
    if (level == null) {
      List classes = ApplicationManager.getApplication().runReadAction(new Computable<List>() {
        @Override
        public List compute() {
          return getStub().getChildrenStubs();
        }
      });
      myLanguageLevel = level = !classes.isEmpty() ? ((PsiClassStub<?>)classes.get(0)).getLanguageLevel() : LanguageLevel.HIGHEST;
    }
    return level;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  @Override
  public void checkSetName(String name) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    buffer.append(BANNER);

    appendText(getPackageStatement(), 0, buffer, "\n\n");

    PsiClass[] classes = getClasses();
    if (classes.length > 0) {
      appendText(classes[0], 0, buffer);
    }
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    PsiElement mirrorElement = SourceTreeToPsiMap.treeToPsiNotNull(element);
    if (!(mirrorElement instanceof PsiJavaFile)) {
      throw new InvalidMirrorException("Unexpected mirror file: " + mirrorElement);
    }

    PsiJavaFile mirrorFile = (PsiJavaFile)mirrorElement;
    setMirrorIfPresent(getPackageStatement(), mirrorFile.getPackageStatement());
    setMirrors(getClasses(), mirrorFile.getClasses());
  }

  @SuppressWarnings("deprecation")
  @Override
  @NotNull
  public PsiElement getNavigationElement() {
    for (ClsCustomNavigationPolicy customNavigationPolicy : Extensions.getExtensions(ClsCustomNavigationPolicy.EP_NAME)) {
      if (customNavigationPolicy instanceof ClsCustomNavigationPolicyEx) {
        PsiFile navigationElement = ((ClsCustomNavigationPolicyEx)customNavigationPolicy).getFileNavigationElement(this);
        if (navigationElement != null) {
          return navigationElement;
        }
      }
    }

    return CachedValuesManager.getCachedValue(this, new CachedValueProvider<PsiElement>() {
      @Nullable
      @Override
      public Result<PsiElement> compute() {
        PsiElement target = JavaPsiImplementationHelper.getInstance(getProject()).getClsFileNavigationElement(ClsFileImpl.this);
        ModificationTracker tracker = FileIndexFacade.getInstance(getProject()).getRootModificationTracker();
        return Result.create(target, ClsFileImpl.this, target.getContainingFile(), tracker);
      }
    });
  }

  @Override
  public PsiElement getMirror() {
    TreeElement mirrorTreeElement = myMirrorFileElement;
    if (mirrorTreeElement == null) {
      synchronized (myMirrorLock) {
        mirrorTreeElement = myMirrorFileElement;
        if (mirrorTreeElement == null) {
          VirtualFile file = getVirtualFile();
          PsiClass[] classes = getClasses();
          String fileName = (classes.length > 0 ? classes[0].getName() : file.getNameWithoutExtension()) + JavaFileType.DOT_DEFAULT_EXTENSION;

          final Document document = FileDocumentManager.getInstance().getDocument(file);
          assert document != null : file.getUrl();

          CharSequence mirrorText = document.getImmutableCharSequence();
          boolean internalDecompiler = StringUtil.startsWith(mirrorText, BANNER);
          PsiFileFactory factory = PsiFileFactory.getInstance(getManager().getProject());
          PsiFile mirror = factory.createFileFromText(fileName, JavaLanguage.INSTANCE, mirrorText, false, false);
          mirror.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, getLanguageLevel());

          mirrorTreeElement = SourceTreeToPsiMap.psiToTreeNotNull(mirror);
          try {
            final TreeElement finalMirrorTreeElement = mirrorTreeElement;
            ProgressManager.getInstance().executeNonCancelableSection(new Runnable() {
              public void run() {
                setMirror(finalMirrorTreeElement);
                putUserData(CLS_DOCUMENT_LINK_KEY, document);
              }
            });
          }
          catch (InvalidMirrorException e) {
            //noinspection ThrowableResultOfMethodCallIgnored
            LOG.error(file.getUrl(), internalDecompiler ? e : wrapException(e, file));
          }

          ((PsiFileImpl)mirror).setOriginalFile(this);
          myMirrorFileElement = mirrorTreeElement;
        }
      }
    }
    return mirrorTreeElement.getPsi();
  }

  private static Exception wrapException(InvalidMirrorException e, VirtualFile file) {
    ClassFileDecompilers.Decompiler decompiler = ClassFileDecompilers.find(file);
    if (decompiler instanceof ClassFileDecompilers.Light) {
      PluginId pluginId = PluginManagerCore.getPluginByClassName(decompiler.getClass().getName());
      if (pluginId != null) {
        return new PluginException(e, pluginId);
      }
    }

    return e;
  }

  @Override
  public PsiFile getDecompiledPsiFile() {
    return (PsiFile)getMirror();
  }

  @Override
  public long getModificationStamp() {
    return getVirtualFile().getModificationStamp();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitJavaFile(this);
    } else {
      visitor.visitFile(this);
    }
  }

  @NonNls
  public String toString() {
    return "PsiFile:" + getName();
  }

  @Override
  @NotNull
  public PsiFile getOriginalFile() {
    return this;
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return JavaClassFileType.INSTANCE;
  }

  @Override
  @NotNull
  public PsiFile[] getPsiRoots() {
    return new PsiFile[]{this};
  }

  @Override
  @NotNull
  public FileViewProvider getViewProvider() {
    return myViewProvider;
  }

  @Override
  public void subtreeChanged() {
  }

  @Override
  public PsiElement getContext() {
    return FileContextUtil.getFileContext(this);
  }

  @Override
  @NotNull
  public PsiClassHolderFileStub<?> getStub() {
    return (PsiClassHolderFileStub)getStubTree().getRoot();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    final ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      final PsiClass[] classes = getClasses();
      for (PsiClass aClass : classes) {
        if (!processor.execute(aClass, state)) return false;
      }
    }
    return true;
  }

  @Override
  @NotNull
  public StubTree getStubTree() {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    StubTree stubTree = dereference(myStub);
    if (stubTree != null) return stubTree;

    // build newStub out of lock to avoid deadlock
    StubTree newStubTree = (StubTree)StubTreeLoader.getInstance().readOrBuild(getProject(), getVirtualFile(), this);
    if (newStubTree == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("No stub for class file in index: " + getVirtualFile().getPresentableUrl());
      }
      newStubTree = new StubTree(new PsiJavaFileStubImpl("corrupted_class_files", true));
    }

    synchronized (myStubLock) {
      stubTree = dereference(myStub);
      if (stubTree != null) return stubTree;

      stubTree = newStubTree;

      @SuppressWarnings("unchecked") PsiFileStubImpl<PsiFile> fileStub = (PsiFileStubImpl)stubTree.getRoot();
      fileStub.setPsi(this);

      myStub = new SoftReference<StubTree>(stubTree);
    }

    return stubTree;
  }

  @Override
  public ASTNode findTreeForStub(final StubTree tree, final StubElement<?> stub) {
    return null;
  }

  @Override
  public boolean isContentsLoaded() {
    return myStub != null;
  }

  @Override
  public void onContentReload() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    synchronized (myStubLock) {
      StubTree stubTree = dereference(myStub);
      myStub = null;
      if (stubTree != null) {
        //noinspection unchecked
        ((PsiFileStubImpl)stubTree.getRoot()).clearPsi("cls onContentReload");
      }
    }

    ClsPackageStatementImpl packageStatement = new ClsPackageStatementImpl(this);
    synchronized (myMirrorLock) {
      putUserData(CLS_DOCUMENT_LINK_KEY, null);
      myMirrorFileElement = null;
      myPackageStatement = packageStatement;
    }

    myLanguageLevel = null;
  }

  @Override
  public void putInfo(@NotNull Map<String, String> info) {
    PsiFileImpl.putInfo(this, info);
  }

  @Override
  public FileASTNode getNode() {
    return null;
  }

  @Override
  public boolean isPhysical() {
    return myIsPhysical;
  }

  @SuppressWarnings("UnusedDeclaration")  // used by Kotlin compiler
  public void setPhysical(boolean isPhysical) {
    myIsPhysical = isPhysical;
  }

  // default decompiler implementation

  @NotNull
  public static CharSequence decompile(@NotNull VirtualFile file) {
    PsiManager manager = PsiManager.getInstance(DefaultProjectFactory.getInstance().getDefaultProject());
    final ClsFileImpl clsFile = new ClsFileImpl(new ClassFileViewProvider(manager, file), true);
    final StringBuilder buffer = new StringBuilder();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        clsFile.appendMirrorText(0, buffer);
      }
    });
    return buffer;
  }

  @Nullable
  public static PsiJavaFileStub buildFileStub(@NotNull VirtualFile file, @NotNull byte[] bytes) throws ClsFormatException {
    if (ClassFileViewProvider.isInnerClass(file)) {
      return null;
    }

    try {
      ClassReader reader = new ClassReader(bytes);
      String internalName = reader.getClassName();
      String className = file.getNameWithoutExtension();
      String fqn = StubBuildingVisitor.getFqn(internalName, className, null);
      String packageName = getPackageName(fqn, className);
      PsiJavaFileStubImpl stub = new PsiJavaFileStubImpl(packageName, true);

      try {
        StubBuildingVisitor<VirtualFile> visitor = new StubBuildingVisitor<VirtualFile>(file, STRATEGY, stub, 0, className);
        reader.accept(visitor, ClassReader.SKIP_FRAMES);
        PsiClassStub<?> result = visitor.getResult();
        if (result == null) return null;
      }
      catch (OutOfOrderInnerClassException e) {
        return null;
      }

      return stub;
    }
    catch (Exception e) {
      throw new ClsFormatException(file.getPath() + ": " + e.getMessage(), e);
    }
  }

  private static final InnerClassSourceStrategy<VirtualFile> STRATEGY = new InnerClassSourceStrategy<VirtualFile>() {
    @Nullable
    @Override
    public VirtualFile findInnerClass(String innerName, VirtualFile outerClass) {
      String baseName = outerClass.getNameWithoutExtension();
      VirtualFile dir = outerClass.getParent();
      assert dir != null : outerClass;
      return dir.findChild(baseName + "$" + innerName + ".class");
    }

    @Override
    public void accept(VirtualFile innerClass, StubBuildingVisitor<VirtualFile> visitor) {
      try {
        byte[] bytes = innerClass.contentsToByteArray();
        new ClassReader(bytes).accept(visitor, ClassReader.SKIP_FRAMES);
      }
      catch (IOException ignored) { }
    }
  };

  private static String getPackageName(String fqn, String shortName) {
    return fqn == null || Comparing.equal(shortName, fqn) ? "" : fqn.substring(0, fqn.lastIndexOf('.'));
  }

  /** @deprecated use {@link #ClsFileImpl(FileViewProvider)} (to remove in IDEA 15) */
  @SuppressWarnings("unused")
  public ClsFileImpl(@NotNull PsiManager manager, @NotNull FileViewProvider viewProvider) {
    this(viewProvider);
  }
}
