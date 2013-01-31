/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.ide.caches.FileContent;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.NonCancelableSection;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.stubs.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.*;

public class ClsFileImpl extends ClsRepositoryPsiElement<PsiClassHolderFileStub> implements PsiJavaFile, PsiFileWithStubSupport, PsiFileEx,
                                                                                            Queryable, PsiClassOwnerEx, PsiCompiledFile {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsFileImpl");

  /** YOU absolutely MUST NOT hold PsiLock under the MIRROR_LOCK */
  private static final MirrorLock MIRROR_LOCK = new MirrorLock();
  private static class MirrorLock {}

  private final PsiManagerImpl myManager;
  private final boolean myIsForDecompiling;
  private final FileViewProvider myViewProvider;
  private volatile SoftReference<StubTree> myStub;
  private volatile TreeElement myMirrorFileElement;
  private volatile ClsPackageStatementImpl myPackageStatement = null;
  private boolean myIsPhysical = true;

  private ClsFileImpl(@NotNull PsiManagerImpl manager, @NotNull FileViewProvider viewProvider, boolean forDecompiling) {
    //noinspection ConstantConditions
    super(null);
    myManager = manager;
    myIsForDecompiling = forDecompiling;
    myViewProvider = viewProvider;
    JavaElementType.CLASS.getIndex(); // Initialize java stubs...
  }

  public ClsFileImpl(PsiManagerImpl manager, FileViewProvider viewProvider) {
    this(manager, viewProvider, false);
  }

  @Override
  public PsiManager getManager() {
    return myManager;
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
    if (myIsForDecompiling) return true;
    VirtualFile vFile = getVirtualFile();
    return vFile.isValid();
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
    final List stubs = getStub().getChildrenStubs();
    return stubs.size() > 0 ? ((PsiClassStub<?>)stubs.get(0)).getLanguageLevel() : LanguageLevel.HIGHEST;
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
  public void appendMirrorText(final int indentLevel, @NotNull final StringBuilder buffer) {
    buffer.append("\n");
    buffer.append("  // IntelliJ API Decompiler stub source generated from a class file\n");
    buffer.append("  // Implementation of methods is not available\n");
    buffer.append("\n");

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

    return JavaPsiImplementationHelper.getInstance(getProject()).getClsFileNavigationElement(this);
  }

  @Override
  public PsiElement getMirror() {
    String mirrorText = null;
    VirtualFile file = null;
    if (myMirrorFileElement == null) {
      file = getVirtualFile();
      // avoid decompiling under MIRROR_LOCK as decompiling can need other locks
      // (e.g. nice parameter names in mirror need resolve and FQN stub index lock) and stub indices (under own lock) need to access stub
      // tree with MIRROR_LOCK, see IDEA-98468
      mirrorText = decompile(getManager(), file);
    }

    synchronized (MIRROR_LOCK) {
      if (myMirrorFileElement == null) {
        String ext = JavaFileType.INSTANCE.getDefaultExtension();
        PsiClass[] classes = getClasses();
        String fileName = (classes.length > 0 ? classes[0].getName() : file.getNameWithoutExtension()) + "." + ext;
        PsiFileFactory factory = PsiFileFactory.getInstance(getManager().getProject());
        PsiFile mirror = factory.createFileFromText(fileName, JavaLanguage.INSTANCE, mirrorText, false, false);
        TreeElement mirrorTreeElement = SourceTreeToPsiMap.psiToTreeNotNull(mirror);

        //IMPORTANT: do not take lock too early - FileDocumentManager.getInstance().saveToString() can run write action...
        final NonCancelableSection section = ProgressIndicatorProvider.startNonCancelableSectionIfSupported();
        try {
          setMirror(mirrorTreeElement);
        }
        catch (InvalidMirrorException e) {
          // todo[r.sh] use logging API once available (to attach .class file)
          LOG.error(file.getPath(), e);
        }
        finally {
          section.done();
        }

        myMirrorFileElement = mirrorTreeElement;
      }

      return myMirrorFileElement.getPsi();
    }
  }

  @Override
  public PsiFile getDecompiledPsiFile() {
    for (ClsFileDecompiledPsiFileProvider provider : Extensions.getExtensions(ClsFileDecompiledPsiFileProvider.EP_NAME)) {
      PsiFile decompiledPsiFile = provider.getDecompiledPsiFile(this);
      if (decompiledPsiFile != null) {
        return decompiledPsiFile;
      }
    }
    return (PsiFile) getMirror();
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

  public static String decompile(PsiManager manager, VirtualFile file) {
    ClsFileImpl psiFile = null;

    final FileViewProvider provider = ((PsiManagerEx)manager).getFileManager().findViewProvider(file);
    if (provider != null) {
      final PsiFile psi = provider.getPsi(provider.getBaseLanguage());
      if (psi instanceof ClsFileImpl) {
        psiFile = (ClsFileImpl)psi;
      }
    }

    if (psiFile == null) {
      psiFile = new ClsFileImpl((PsiManagerImpl)manager, new ClassFileViewProvider(manager, file), true);
    }

    final StringBuilder buffer = new StringBuilder();
    psiFile.appendMirrorText(0, buffer);
    return buffer.toString();
  }

  @Override
  public PsiElement getContext() {
    return FileContextUtil.getFileContext(this);
  }

  @Override
  @NotNull
  public PsiClassHolderFileStub getStub() {
    return (PsiClassHolderFileStub)getStubTree().getRoot();
  }

  private final Object lock = new Object();

  @Override
  @NotNull
  public StubTree getStubTree() {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final StubTree derefd = derefStub();
    if (derefd != null) return derefd;

    StubTree stubHolder = (StubTree)StubTreeLoader.getInstance().readOrBuild(getProject(), getVirtualFile(), this);
    if (stubHolder == null) {
      // Must be corrupted .class file
      LOG.info("Class file is corrupted: " + getVirtualFile().getPresentableUrl());

      StubTree emptyTree = new StubTree(new PsiJavaFileStubImpl("corrupted.classfiles", true));
      setStubTree(emptyTree);
      resetMirror();
      return emptyTree;
    }

    synchronized (lock) {
      final StubTree derefdOnLock = derefStub();
      if (derefdOnLock != null) return derefdOnLock;

      setStubTree(stubHolder);
    }

    resetMirror();
    return stubHolder;
  }

  private void setStubTree(StubTree tree) {
    synchronized (lock) {
      myStub = new SoftReference<StubTree>(tree);
      ((PsiFileStubImpl)tree.getRoot()).setPsi(this);
    }
  }

  private void resetMirror() {
    ClsPackageStatementImpl clsPackageStatement = new ClsPackageStatementImpl(this);
    synchronized (MIRROR_LOCK) {
      myMirrorFileElement = null;
      myPackageStatement = clsPackageStatement;
    }
  }

  @Nullable
  private StubTree derefStub() {
    synchronized (lock) {
      return myStub != null ? myStub.get() : null;
    }
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
    SoftReference<StubTree> stub = myStub;
    StubTree stubHolder = stub == null ? null : stub.get();
    if (stubHolder != null) {
      ((StubBase<?>)stubHolder.getRoot()).setPsi(null);
    }
    myStub = null;

    ApplicationManager.getApplication().assertWriteAccessAllowed();

    synchronized (MIRROR_LOCK) {
      myMirrorFileElement = null;
      myPackageStatement = null;
    }
  }

  @Override
  public PsiFile cacheCopy(final FileContent content) {
    return this;
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
}
