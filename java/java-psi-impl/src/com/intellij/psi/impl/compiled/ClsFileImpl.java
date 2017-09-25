/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.compiled.ClassFileDecompilers;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.file.PsiFileImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
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
import com.intellij.util.BitUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.cls.ClsFormatException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.Attribute;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.lang.ref.Reference;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

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
  private volatile Reference<TreeElement> myMirrorFileElement;
  private volatile ClsPackageStatementImpl myPackageStatement;
  private boolean myIsPhysical = true;
  private boolean myInvalidated;

  public ClsFileImpl(@NotNull FileViewProvider viewProvider) {
    this(viewProvider, false);
  }

  private ClsFileImpl(@NotNull FileViewProvider viewProvider, boolean forDecompiling) {
    super(null);
    myViewProvider = viewProvider;
    myIsForDecompiling = forDecompiling;
    //noinspection ResultOfMethodCallIgnored
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
    return !myInvalidated && (myIsForDecompiling || getVirtualFile().isValid());
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {}

  @Override
  public void delete() throws IncorrectOperationException {
    checkDelete();
    PsiFileImplUtil.doDelete(this);
  }

  boolean isForDecompiling() {
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
    PsiJavaModule module = getModuleDeclaration();
    return module != null ? new PsiElement[]{module} : getClasses();
  }

  @Override
  @NotNull
  public PsiClass[] getClasses() {
    return getStub().getClasses();
  }

  @Override
  public PsiPackageStatement getPackageStatement() {
    ClsPackageStatementImpl statement = myPackageStatement;
    if (statement == null) {
      statement = ClsPackageStatementImpl.NULL_PACKAGE;
      PsiClassHolderFileStub<?> stub = getStub();
      if (!(stub instanceof PsiJavaFileStub) || stub.findChildStubByType(JavaStubElementTypes.MODULE) == null) {
        String packageName = findPackageName(stub);
        if (packageName != null) {
          statement = new ClsPackageStatementImpl(this, packageName);
        }
      }
      myPackageStatement = statement;
    }
    return statement != ClsPackageStatementImpl.NULL_PACKAGE ? statement : null;
  }

  private static String findPackageName(PsiClassHolderFileStub<?> stub) {
    String packageName = null;

    if (stub instanceof PsiJavaFileStub) {
      packageName = ((PsiJavaFileStub)stub).getPackageName();
    }
    else {
      PsiClass[] psiClasses = stub.getClasses();
      if (psiClasses.length > 0) {
        String className = psiClasses[0].getQualifiedName();
        if (className != null) {
          int index = className.lastIndexOf('.');
          if (index >= 0) {
            packageName = className.substring(0, index);
          }
        }
      }
    }

    return !StringUtil.isEmpty(packageName) ? packageName : null;
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
    PsiClassHolderFileStub<?> stub = getStub();
    if (stub instanceof PsiJavaFileStub) {
      LanguageLevel level = ((PsiJavaFileStub)stub).getLanguageLevel();
      if (level != null) {
        return level;
      }
    }
    return LanguageLevel.HIGHEST;
  }

  @Nullable
  @Override
  public PsiJavaModule getModuleDeclaration() {
    PsiClassHolderFileStub<?> stub = getStub();
    return stub instanceof PsiJavaFileStub ? ((PsiJavaFileStub)stub).getModule() : null;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public void checkSetName(String name) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    buffer.append(BANNER);

    PsiJavaModule module = getModuleDeclaration();
    if (module != null) {
      appendText(module, 0, buffer);
    }
    else {
      appendText(getPackageStatement(), 0, buffer, "\n\n");

      PsiClass[] classes = getClasses();
      if (classes.length > 0) {
        appendText(classes[0], 0, buffer);
      }
    }
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    PsiElement mirrorElement = SourceTreeToPsiMap.treeToPsiNotNull(element);
    if (!(mirrorElement instanceof PsiJavaFile)) {
      throw new InvalidMirrorException("Unexpected mirror file: " + mirrorElement);
    }

    PsiJavaFile mirrorFile = (PsiJavaFile)mirrorElement;
    PsiJavaModule module = getModuleDeclaration();
    if (module != null) {
      setMirror(module, mirrorFile.getModuleDeclaration());
    }
    else {
      setMirrorIfPresent(getPackageStatement(), mirrorFile.getPackageStatement());
      setMirrors(getClasses(), mirrorFile.getClasses());
    }
  }

  @Override
  @NotNull
  @SuppressWarnings("deprecation")
  public PsiElement getNavigationElement() {
    for (ClsCustomNavigationPolicy customNavigationPolicy : Extensions.getExtensions(ClsCustomNavigationPolicy.EP_NAME)) {
      if (customNavigationPolicy instanceof ClsCustomNavigationPolicyEx) {
        try {
          PsiFile navigationElement = ((ClsCustomNavigationPolicyEx)customNavigationPolicy).getFileNavigationElement(this);
          if (navigationElement != null) {
            return navigationElement;
          }
        }
        catch (IndexNotReadyException ignore) { }
      }
    }

    return CachedValuesManager.getCachedValue(this, () -> {
      PsiElement target = JavaPsiImplementationHelper.getInstance(getProject()).getClsFileNavigationElement(this);
      ModificationTracker tracker = FileIndexFacade.getInstance(getProject()).getRootModificationTracker();
      return CachedValueProvider.Result.create(target, this, target.getContainingFile(), tracker);
    });
  }

  @Override
  public PsiElement getMirror() {
    TreeElement mirrorTreeElement = SoftReference.dereference(myMirrorFileElement);
    if (mirrorTreeElement == null) {
      synchronized (myMirrorLock) {
        mirrorTreeElement = SoftReference.dereference(myMirrorFileElement);
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
            ProgressManager.getInstance().executeNonCancelableSection(() -> {
              setMirror(finalMirrorTreeElement);
              putUserData(CLS_DOCUMENT_LINK_KEY, document);
            });
          }
          catch (InvalidMirrorException e) {
            //noinspection ThrowableResultOfMethodCallIgnored
            LOG.error(file.getUrl(), internalDecompiler ? e : wrapException(e, file));
          }

          ((PsiFileImpl)mirror).setOriginalFile(this);
          myMirrorFileElement = new SoftReference<>(mirrorTreeElement);
        }
      }
    }
    return mirrorTreeElement.getPsi();
  }

  @Override
  public String getText() {
    VirtualFile file = getVirtualFile();
    Document document = FileDocumentManager.getInstance().getDocument(file);
    assert document != null : file.getUrl();
    return document.getText();
  }

  @Override
  public int getTextLength() {
    VirtualFile file = getVirtualFile();
    Document document = FileDocumentManager.getInstance().getDocument(file);
    assert document != null : file.getUrl();
    return document.getTextLength();
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

    StubTree stubTree = SoftReference.dereference(myStub);
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
      stubTree = SoftReference.dereference(myStub);
      if (stubTree != null) return stubTree;

      stubTree = newStubTree;

      @SuppressWarnings("unchecked") PsiFileStubImpl<PsiFile> fileStub = (PsiFileStubImpl)stubTree.getRoot();
      fileStub.setPsi(this);

      myStub = new SoftReference<>(stubTree);
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
      StubTree stubTree = SoftReference.dereference(myStub);
      myStub = null;
      if (stubTree != null) {
        //noinspection unchecked
        ((PsiFileStubImpl)stubTree.getRoot()).clearPsi("cls onContentReload");
      }
    }

    synchronized (myMirrorLock) {
      putUserData(CLS_DOCUMENT_LINK_KEY, null);
      myMirrorFileElement = null;
      myPackageStatement = null;
    }
  }

  @Override
  public void markInvalidated() {
    myInvalidated = true;
    DebugUtil.onInvalidated(this);
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

  /** @deprecated override {@link #isPhysical()} instead (to be removed in IDEA 17) */
  @SuppressWarnings("UnusedDeclaration")
  public void setPhysical(boolean isPhysical) {
    myIsPhysical = isPhysical;
  }

  // default decompiler implementation

  @NotNull
  public static CharSequence decompile(@NotNull VirtualFile file) {
    PsiManager manager = PsiManager.getInstance(DefaultProjectFactory.getInstance().getDefaultProject());
    final ClsFileImpl clsFile = new ClsFileImpl(new ClassFileViewProvider(manager, file), true);
    final StringBuilder buffer = new StringBuilder();
    ApplicationManager.getApplication().runReadAction(() -> clsFile.appendMirrorText(0, buffer));
    return buffer;
  }

  @Nullable
  public static PsiJavaFileStub buildFileStub(@NotNull VirtualFile file, @NotNull byte[] bytes) throws ClsFormatException {
    try {
      if (ClassFileViewProvider.isInnerClass(file, bytes)) {
        return null;
      }

      ClassReader reader = new ClassReader(bytes);
      String className = file.getNameWithoutExtension();
      String internalName = reader.getClassName();
      boolean module = internalName.equals("module-info") && BitUtil.isSet(reader.getAccess(), Opcodes.ACC_MODULE);
      LanguageLevel level = ClsParsingUtil.getLanguageLevelByVersion(reader.readShort(6));

      if (module) {
        PsiJavaFileStub stub = new PsiJavaFileStubImpl(null, "", level, true);
        ModuleStubBuildingVisitor visitor = new ModuleStubBuildingVisitor(stub);
        reader.accept(visitor, EMPTY_ATTRIBUTES, ClassReader.SKIP_FRAMES);
        if (visitor.getResult() != null) return stub;
      }
      else {
        PsiJavaFileStub stub = new PsiJavaFileStubImpl(null, getPackageName(internalName), level, true);
        try {
          FileContentPair source = new FileContentPair(file, bytes);
          StubBuildingVisitor<FileContentPair> visitor = new StubBuildingVisitor<>(source, STRATEGY, stub, 0, className);
          reader.accept(visitor, EMPTY_ATTRIBUTES, ClassReader.SKIP_FRAMES);
          if (visitor.getResult() != null) return stub;
        }
        catch (OutOfOrderInnerClassException e) {
          if (LOG.isTraceEnabled()) LOG.trace(file.getPath());
        }
      }

      return null;
    }
    catch (Exception e) {
      throw new ClsFormatException(file.getPath() + ": " + e.getMessage(), e);
    }
  }

  private static String getPackageName(String internalName) {
    int p = internalName.lastIndexOf('/');
    return p > 0 ? internalName.substring(0, p).replace('/', '.') : "";
  }

  static class FileContentPair extends Pair<VirtualFile, byte[]> {
    public FileContentPair(@NotNull VirtualFile file, @NotNull byte[] content) {
      super(file, content);
    }

    @NotNull
    public byte[] getContent() {
      return second;
    }

    @Override
    public String toString() {
      return first.toString();
    }
  }

  private static final InnerClassSourceStrategy<FileContentPair> STRATEGY = new InnerClassSourceStrategy<FileContentPair>() {
    @Nullable
    @Override
    public FileContentPair findInnerClass(String innerName, FileContentPair outerClass) {
      String baseName = outerClass.first.getNameWithoutExtension();
      VirtualFile dir = outerClass.first.getParent();
      assert dir != null : outerClass;
      VirtualFile innerClass = dir.findChild(baseName + '$' + innerName + ".class");
      if (innerClass != null) {
        try {
          byte[] bytes = innerClass.contentsToByteArray(false);
          return new FileContentPair(innerClass, bytes);
        }
        catch (IOException ignored) { }
      }
      return null;
    }

    @Override
    public void accept(FileContentPair innerClass, StubBuildingVisitor<FileContentPair> visitor) {
      new ClassReader(innerClass.second).accept(visitor, EMPTY_ATTRIBUTES, ClassReader.SKIP_FRAMES);
    }
  };

  public static final Attribute[] EMPTY_ATTRIBUTES = new Attribute[0];
}