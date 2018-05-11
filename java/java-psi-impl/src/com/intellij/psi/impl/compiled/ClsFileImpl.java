// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.compiled.ClassFileDecompilers;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.compiled.ClsElementImpl.InvalidMirrorException;
import com.intellij.psi.impl.file.PsiBinaryFileImpl;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.StubbedSpine;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.PsiClassHolderFileStub;
import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.stubs.StubTreeLoader;
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

public class ClsFileImpl extends PsiBinaryFileImpl
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

  private final boolean myIsForDecompiling;
  private volatile SoftReference<StubTree> myStub;
  private volatile Reference<TreeElement> myMirrorFileElement;
  private volatile ClsPackageStatementImpl myPackageStatement;

  public ClsFileImpl(@NotNull FileViewProvider viewProvider) {
    this(viewProvider, false);
  }

  private ClsFileImpl(@NotNull FileViewProvider viewProvider, boolean forDecompiling) {
    super((PsiManagerImpl)viewProvider.getManager(), viewProvider);
    myIsForDecompiling = forDecompiling;
    //noinspection ResultOfMethodCallIgnored
    JavaElementType.CLASS.getIndex();  // initialize Java stubs
  }

  @Override
  public PsiFile getContainingFile() {
    if (!isValid()) throw new PsiInvalidElementAccessException(this);
    return this;
  }

  @Override
  public boolean isValid() {
    return super.isValid() && (myIsForDecompiling || getVirtualFile().isValid());
  }

  boolean isForDecompiling() {
    return myIsForDecompiling;
  }

  @Override
  @NotNull
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
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
    throw ClsElementImpl.cannotModifyException(this);
  }

  @Override
  public void checkSetName(String name) throws IncorrectOperationException {
    throw ClsElementImpl.cannotModifyException(this);
  }

  /** Shouldn't be called from outside or overridden */
  @Deprecated
  public void appendMirrorText(@SuppressWarnings("unused") int indentLevel, @NotNull StringBuilder buffer) {
    appendMirrorText(buffer);
  }

  private void appendMirrorText(@NotNull StringBuilder buffer) {
    buffer.append(BANNER);

    PsiJavaModule module = getModuleDeclaration();
    if (module != null) {
      ClsElementImpl.appendText(module, 0, buffer);
    }
    else {
      ClsElementImpl.appendText(getPackageStatement(), 0, buffer, "\n\n");

      PsiClass[] classes = getClasses();
      if (classes.length > 0) {
        ClsElementImpl.appendText(classes[0], 0, buffer);
      }
    }
  }

  /** Shouldn't be called from outside or overridden */
  @Deprecated
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setFileMirror(element);
  }

  private void setFileMirror(@NotNull TreeElement element) {
    PsiElement mirrorElement = SourceTreeToPsiMap.treeToPsiNotNull(element);
    if (!(mirrorElement instanceof PsiJavaFile)) {
      throw new InvalidMirrorException("Unexpected mirror file: " + mirrorElement);
    }

    PsiJavaFile mirrorFile = (PsiJavaFile)mirrorElement;
    PsiJavaModule module = getModuleDeclaration();
    if (module != null) {
      ClsElementImpl.setMirror(module, mirrorFile.getModuleDeclaration());
    }
    else {
      ClsElementImpl.setMirrorIfPresent(getPackageStatement(), mirrorFile.getPackageStatement());
      ClsElementImpl.setMirrors(getClasses(), mirrorFile.getClasses());
    }
  }

  @Override
  @NotNull
  public PsiElement getNavigationElement() {
    //noinspection deprecation
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
  @NotNull
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
              setFileMirror(finalMirrorTreeElement);
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
  public final TextRange getTextRange() {
    return TextRange.create(0, getTextLength());
  }

  @Override
  public final int getStartOffsetInParent() {
    return 0;
  }

  @Override
  public final PsiElement findElementAt(int offset) {
    return getMirror().findElementAt(offset);
  }

  @Override
  public PsiReference findReferenceAt(int offset) {
    return getMirror().findReferenceAt(offset);
  }

  @Override
  public final int getTextOffset() {
    return 0;
  }

  @Override
  @NotNull
  public char[] textToCharArray() {
    return getMirror().textToCharArray();
  }

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

  @NotNull
  @Override
  public StubbedSpine getStubbedSpine() {
    return getStubTree().getSpine();
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
  public void putInfo(@NotNull Map<String, String> info) {
    PsiFileImpl.putInfo(this, info);
  }

  // default decompiler implementation

  @NotNull
  public static CharSequence decompile(@NotNull VirtualFile file) {
    PsiManager manager = PsiManager.getInstance(DefaultProjectFactory.getInstance().getDefaultProject());
    final ClsFileImpl clsFile = new ClsFileImpl(new ClassFileViewProvider(manager, file), true);
    final StringBuilder buffer = new StringBuilder();
    ApplicationManager.getApplication().runReadAction(() -> clsFile.appendMirrorText(buffer));
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
      JavaSdkVersion jdkVersion = ClsParsingUtil.getJdkVersionByBytecode(reader.readShort(6));
      LanguageLevel level = jdkVersion != null ? jdkVersion.getMaxLanguageLevel() : null;

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