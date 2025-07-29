// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContextManagerImpl;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
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
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.compiled.ClsElementImpl.InvalidMirrorException;
import com.intellij.psi.impl.file.PsiBinaryFileImpl;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiPackageStatementStubImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.StubbedSpine;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.AstLoadingFilter;
import com.intellij.util.BitUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.cls.ClsFormatException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.Attribute;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.intellij.codeInsight.multiverse.CodeInsightContexts.isSharedSourceSupportEnabled;
import static com.intellij.reference.SoftReference.dereference;
import static com.intellij.util.ObjectUtils.notNull;

public class ClsFileImpl extends PsiBinaryFileImpl
                         implements PsiJavaFile, PsiFileWithStubSupport, PsiFileEx, Queryable, PsiClassOwnerEx, PsiCompiledFile {
  private static final Logger LOG = Logger.getInstance(ClsFileImpl.class);

  private static final String BANNER =
    "\n" +
    "  // IntelliJ API Decompiler stub source generated from a class file\n" +
    "  // Implementation of methods is not available\n" +
    "\n";
  private static final String CORRUPTED_CLASS_PACKAGE = "corrupted_class_file";

  private static final Key<Document> CLS_DOCUMENT_LINK_KEY = Key.create("cls.document.link");

  private final Object myMirrorLock = new Object();  // NOTE: one absolutely MUST NOT hold PsiLock under the mirror lock
  private final Object myStubLock = new Object();

  private final boolean myIsForDecompiling;
  private volatile SoftReference<StubTree> myStub;
  private volatile Reference<TreeElement> myMirrorFileElement;

  public ClsFileImpl(@NotNull FileViewProvider viewProvider) {
    this(viewProvider, false);
  }

  private ClsFileImpl(@NotNull FileViewProvider viewProvider, boolean forDecompiling) {
    super((PsiManagerEx)viewProvider.getManager(), viewProvider);
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
  public @NotNull Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    PsiJavaModule module = getModuleDeclaration();
    return module != null ? new PsiElement[]{module} : getClasses();
  }

  @Override
  public PsiClass @NotNull [] getClasses() {
    return getStub().getClasses();
  }

  @Override
  public PsiPackageStatement getPackageStatement() {
    @SuppressWarnings("unchecked") final StubElement<PsiPackageStatement> child =
      (StubElement<PsiPackageStatement>)getStub().findChildStubByElementType(JavaStubElementTypes.PACKAGE_STATEMENT);
    return child == null ? null : child.getPsi();
  }

  @Override
  public @NotNull String getPackageName() {
    return ((PsiJavaFileStub)getStub()).getPackageName();
  }

  @Override
  public void setPackageName(@NotNull String packageName) throws IncorrectOperationException {
    throw new IncorrectOperationException("Cannot set package name for compiled files");
  }

  @Override
  public PsiImportList getImportList() {
    return null;
  }

  @Override
  public boolean importClass(@NotNull PsiClass aClass) {
    throw new UnsupportedOperationException("Cannot add imports to compiled classes");
  }

  @Override
  public PsiElement @NotNull [] getOnDemandImports(boolean includeImplicit, boolean checkIncludes) {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  @Override
  public PsiClass @NotNull [] getSingleClassImports(boolean checkIncludes) {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  public String @NotNull [] getImplicitlyImportedPackages() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public Set<String> getClassNames() {
    return Collections.singleton(getVirtualFile().getNameWithoutExtension());
  }

  @Override
  public PsiJavaCodeReferenceElement @NotNull [] getImplicitlyImportedPackageReferences() {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  @Override
  public PsiJavaCodeReferenceElement findImportReferenceTo(PsiClass aClass) {
    return null;
  }

  @Override
  public @NotNull LanguageLevel getLanguageLevel() {
    PsiClassHolderFileStub<?> stub = getStub();
    if (stub instanceof PsiJavaFileStub) {
      LanguageLevel level = ((PsiJavaFileStub)stub).getLanguageLevel();
      if (level != null) {
        return level;
      }
    }
    return LanguageLevel.HIGHEST;
  }

  @Override
  public @Nullable PsiJavaModule getModuleDeclaration() {
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

  private void appendMirrorText(StringBuilder buffer) {
    buffer.append(BANNER);

    PsiJavaModule module = getModuleDeclaration();
    if (module != null) {
      ClsElementImpl.appendText(module, 0, buffer);
    }
    else {
      PsiClass[] classes = getClasses();
      if (classes.length > 0) {
        PsiClass topClass = classes[0];
        ClsElementImpl.appendText(getPackageStatement(), 0, buffer, "\n\n");
        if (!PsiPackage.PACKAGE_INFO_CLASS.equals(topClass.getName())) {
          ClsElementImpl.appendText(topClass, 0, buffer);
        }
      }
    }
  }

  private void setFileMirror(TreeElement element) {
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
      PsiClass[] classes = getClasses(), mirrors = mirrorFile.getClasses();
      PsiPackageStatement pkg = getPackageStatement(), mirrorPkg = mirrorFile.getPackageStatement();
      if (classes.length == 1 && mirrors.length == 0 && PsiPackage.PACKAGE_INFO_CLASS.equals(classes[0].getName())) {
        ClsElementImpl.setMirror(pkg, mirrorPkg);
        ClsElementImpl.setMirrorIfPresent(classes[0].getModifierList(), mirrorPkg.getAnnotationList());
      }
      else if (pkg == null || !CORRUPTED_CLASS_PACKAGE.equals(pkg.getPackageName())) {
        ClsElementImpl.setMirrorIfPresent(pkg, mirrorPkg);
        ClsElementImpl.setMirrors(classes, mirrors);
      }
    }
  }

  @Override
  public @NotNull PsiElement getNavigationElement() {
    for (ClsCustomNavigationPolicy navigationPolicy : ClsCustomNavigationPolicy.EP_NAME.getExtensionList()) {
      try {
        PsiElement navigationElement = navigationPolicy.getNavigationElement(this);
        if (navigationElement != null) return navigationElement;
      }
      catch (IndexNotReadyException ignore) { }
    }

    return CachedValuesManager.getCachedValue(this, () -> {
      PsiElement target = JavaPsiImplementationHelper.getInstance(getProject()).getClsFileNavigationElement(this);
      ModificationTracker tracker = FileIndexFacade.getInstance(getProject()).getRootModificationTracker();
      return CachedValueProvider.Result.create(target, this, target.getContainingFile(), tracker);
    });
  }

  @Override
  public @NotNull PsiElement getMirror() {
    TreeElement mirrorTreeElement = dereference(myMirrorFileElement);
    if (mirrorTreeElement == null) {
      synchronized (myMirrorLock) {
        mirrorTreeElement = dereference(myMirrorFileElement);
        if (mirrorTreeElement == null) {
          VirtualFile file = getVirtualFile();
          AstLoadingFilter.assertTreeLoadingAllowed(file);

          PsiClass[] classes = getClasses();
          String fileName = (classes.length > 0 ? classes[0].getName() : file.getNameWithoutExtension()) + JavaFileType.DOT_DEFAULT_EXTENSION;

          Document document = FileDocumentManager.getInstance().getDocument(file);
          assert document != null : file.getUrl();

          CharSequence mirrorText = document.getImmutableCharSequence();
          boolean internalDecompiler = StringUtil.startsWith(mirrorText, BANNER);
          PsiFileFactory factory = PsiFileFactory.getInstance(getManager().getProject());
          PsiFile mirror = factory.createFileFromText(fileName, JavaLanguage.INSTANCE, mirrorText, false, false, true);
          mirror.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, getLanguageLevel());
          if (isSharedSourceSupportEnabled(getProject())) {
            CodeInsightContextManagerImpl contextManager = CodeInsightContextManagerImpl.getInstanceImpl(getProject());
            CodeInsightContext context = contextManager.getCodeInsightContext(getViewProvider());
            contextManager.setCodeInsightContext(mirror.getViewProvider(), context);
          }

          mirrorTreeElement = SourceTreeToPsiMap.psiToTreeNotNull(mirror);
          try {
            TreeElement _mirrorTreeElement = mirrorTreeElement;
            ProgressManager.getInstance().executeNonCancelableSection(() -> {
              setFileMirror(_mirrorTreeElement);
              putUserData(CLS_DOCUMENT_LINK_KEY, document);
            });
          }
          catch (InvalidMirrorException e) {
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
    ClassFileDecompilers.Decompiler decompiler = ClassFileDecompilers.getInstance().find(file, ClassFileDecompilers.Light.class);
    if (decompiler != null) {
      PluginDescriptor plugin = PluginManager.getPluginByClass(decompiler.getClass());
      if (plugin != null) {
        return new PluginException(e, plugin.getPluginId());
      }
    }

    return e;
  }

  @Override
  public @NotNull PsiFile getDecompiledPsiFile() {
    return (PsiFile)getMirror();
  }

  @Override
  public @Nullable PsiFile getCachedMirror() {
    TreeElement mirrorTreeElement = dereference(myMirrorFileElement);
    return mirrorTreeElement == null ? null : (PsiFile)mirrorTreeElement.getPsi();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitJavaFile(this);
    }
    else {
      visitor.visitFile(this);
    }
  }

  @Override
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
  public char @NotNull [] textToCharArray() {
    return getMirror().textToCharArray();
  }

  public @NotNull PsiClassHolderFileStub<?> getStub() {
    return (PsiClassHolderFileStub<?>)getStubTree().getRoot();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      for (PsiClass aClass : getClasses()) {
        if (!processor.execute(aClass, state)) return false;
      }
    }
    return true;
  }

  @Override
  public @NotNull StubTree getStubTree() {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    StubTree stubTree = dereference(myStub);
    if (stubTree != null) return stubTree;

    // build newStub out of lock to avoid deadlock
    StubTreeLoader stubTreeLoader = StubTreeLoader.getInstance();
    Project project = getProject();
    VirtualFile virtualFile = getVirtualFile();
    boolean isDefault = project.isDefault(); // happens on decompile
    StubTree newStubTree =
      (StubTree)(isDefault ? stubTreeLoader.build(null, virtualFile, this) : stubTreeLoader.readOrBuild(project, virtualFile, this));
    if (newStubTree == null) {
      if (LOG.isDebugEnabled()) LOG.debug("No stub for class file " + virtualFile.getPresentableUrl());
      newStubTree = getCorruptedClassTree();
    }
    else if (!(newStubTree.getRoot() instanceof PsiClassHolderFileStub)) {
      if (LOG.isDebugEnabled()) LOG.debug("Invalid stub for class file " + virtualFile.getPresentableUrl() + ": " + newStubTree.getRoot());
      newStubTree = getCorruptedClassTree();
    }

    synchronized (myStubLock) {
      stubTree = dereference(myStub);
      if (stubTree != null) return stubTree;

      stubTree = newStubTree;

      @SuppressWarnings("unchecked") PsiFileStubImpl<PsiFile> fileStub = (PsiFileStubImpl<PsiFile>)stubTree.getRoot();
      fileStub.setPsi(this);

      myStub = new SoftReference<>(stubTree);
    }

    return stubTree;
  }

  private static @NotNull StubTree getCorruptedClassTree() {
    PsiJavaFileStubImpl root = new PsiJavaFileStubImpl(true);
    //noinspection ResultOfObjectAllocationIgnored
    new PsiPackageStatementStubImpl(root, CORRUPTED_CLASS_PACKAGE);
    return new StubTree(root);
  }

  @Override
  public @NotNull StubbedSpine getStubbedSpine() {
    return getStubTree().getSpine();
  }

  @Override
  public boolean isContentsLoaded() {
    return getCachedMirror() != null;
  }

  @Override
  public void onContentReload() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    synchronized (myStubLock) {
      StubTree stubTree = dereference(myStub);
      myStub = null;
      if (stubTree != null) {
        ((PsiFileStubImpl<?>)stubTree.getRoot()).clearPsi("cls onContentReload");
      }
    }

    synchronized (myMirrorLock) {
      putUserData(CLS_DOCUMENT_LINK_KEY, null);
      myMirrorFileElement = null;
    }
  }

  @Override
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
    PsiFileImpl.putInfo(this, info);
  }

  // default decompiler implementation

  public static @NotNull CharSequence decompile(@NotNull VirtualFile file) {
    PsiManager manager = PsiManager.getInstance(DefaultProjectFactory.getInstance().getDefaultProject());
    ClsFileImpl clsFile = new ClsFileImpl(new ClassFileViewProvider(manager, file), true);
    StringBuilder buffer = new StringBuilder();
    ApplicationManager.getApplication().runReadAction(() -> clsFile.appendMirrorText(buffer));
    return buffer;
  }

  public static @Nullable PsiJavaFileStub buildFileStub(@NotNull VirtualFile file, byte @NotNull [] bytes) throws ClsFormatException {
    try {
      ClassReader reader = new ClassReader(bytes);
      if (ClassFileViewProvider.isInnerClass(file, reader)) return null;

      String className = file.getNameWithoutExtension();
      String internalName = reader.getClassName();
      boolean module = internalName.equals("module-info") && BitUtil.isSet(reader.getAccess(), Opcodes.ACC_MODULE);
      JavaSdkVersion jdkVersion = ClsParsingUtil.getJdkVersionByBytecode(reader.readUnsignedShort(6));
      LanguageLevel level = jdkVersion != null ? jdkVersion.getMaxLanguageLevel() : null;
      if (level != null && level.isAtLeast(LanguageLevel.JDK_11) && ClsParsingUtil.isPreviewLevel(reader.readUnsignedShort(4))) {
        level = notNull(level.getPreviewLevel(), LanguageLevel.HIGHEST);
      }

      PsiJavaFileStub stub = new PsiJavaFileStubImpl(null, level, true);
      if (module) {
        ModuleStubBuildingVisitor visitor = new ModuleStubBuildingVisitor(stub);
        reader.accept(visitor, visitor.attributes(), ClassReader.SKIP_FRAMES);
        if (visitor.getResult() != null) return stub;
      }
      else {
        try {
          FileContentPair source = new FileContentPair(file, reader);
          StubBuildingVisitor<FileContentPair> visitor = new StubBuildingVisitor<>(source, STRATEGY, stub, 0, className);
          reader.accept(visitor, EMPTY_ATTRIBUTES, ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE | ClassReader.VISIT_LOCAL_VARIABLES);
          if (visitor.getResult() != null) return stub;
        }
        catch (OutOfOrderInnerClassException e) {
          if (LOG.isTraceEnabled()) LOG.trace(file.getPath());
        }
      }

      return null;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new ClsFormatException(file.getPath() + ": " + e.getMessage(), e);
    }
  }

  static class FileContentPair extends Pair<VirtualFile, ClassReader> {
    FileContentPair(@NotNull VirtualFile file, @NotNull ClassReader content) {
      super(file, content);
    }

    public @NotNull ClassReader getContent() {
      return second;
    }

    @Override
    public String toString() {
      return first.toString();
    }
  }

  private static final InnerClassSourceStrategy<FileContentPair> STRATEGY = new InnerClassSourceStrategy<FileContentPair>() {
    @Override
    public @Nullable FileContentPair findInnerClass(String innerName, FileContentPair outerClass) {
      String baseName = outerClass.first.getNameWithoutExtension();
      VirtualFile dir = outerClass.first.getParent();
      assert dir != null : outerClass;
      VirtualFile innerClass = dir.findChild(baseName + '$' + innerName + ".class");
      if (innerClass != null) {
        try {
          byte[] bytes = innerClass.contentsToByteArray(false);
          return new FileContentPair(innerClass, new ClassReader(bytes));
        }
        catch (IOException ignored) { }
      }
      return null;
    }

    @Override
    public void accept(FileContentPair innerClass, StubBuildingVisitor<FileContentPair> visitor) {
      try {
        innerClass.second.accept(visitor, EMPTY_ATTRIBUTES, ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE | ClassReader.VISIT_LOCAL_VARIABLES);
      }
      catch (Exception e) {  // workaround for bug in skipping annotations when a first parameter of inner class is dropped (IDEA-204145)
        VirtualFile file = innerClass.first;
        if (LOG.isDebugEnabled()) LOG.debug(String.valueOf(file), e);
        else LOG.info(file + ": " + e.getMessage());
      }
    }
  };

  public static final Attribute[] EMPTY_ATTRIBUTES = new Attribute[0];
}
