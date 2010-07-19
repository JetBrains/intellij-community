/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiManagerImpl;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClsFileImpl extends ClsRepositoryPsiElement<PsiClassHolderFileStub> implements PsiJavaFile, PsiFileWithStubSupport, PsiFileEx,
                                                                                            Queryable, PsiClassOwnerEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsFileImpl");

  static final Object MIRROR_LOCK = new String("Mirror Lock");

  private static final Key<Document> DOCUMENT_IN_MIRROR_KEY = Key.create("DOCUMENT_IN_MIRROR_KEY");
  private final PsiManagerImpl myManager;
  private final boolean myIsForDecompiling;
  private final FileViewProvider myViewProvider;
  private volatile SoftReference<StubTree> myStub;
  private TreeElement myMirrorFileElement;
  private volatile ClsPackageStatementImpl myPackageStatement = null;

  private ClsFileImpl(@NotNull PsiManagerImpl manager, @NotNull FileViewProvider viewProvider, boolean forDecompiling) {
    super(null);
    myManager = manager;
    JavaElementType.CLASS.getIndex(); // Initialize java stubs...

    myIsForDecompiling = forDecompiling;
    myViewProvider = viewProvider;
  }

  public ClsFileImpl(PsiManagerImpl manager, FileViewProvider viewProvider) {
    this(manager, viewProvider, false);
  }

  public PsiManager getManager() {
    return myManager;
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myViewProvider.getVirtualFile();
  }

  public boolean processChildren(final PsiElementProcessor<PsiFileSystemItem> processor) {
    return true;
  }

  public PsiDirectory getParent() {
    return getContainingDirectory();
  }

  public PsiDirectory getContainingDirectory() {
    VirtualFile parentFile = getVirtualFile().getParent();
    if (parentFile == null) return null;
    return getManager().findDirectory(parentFile);
  }

  public PsiFile getContainingFile() {
    if (!isValid()) throw new PsiInvalidElementAccessException(this);
    return this;
  }

  public boolean isValid() {
    if (myIsForDecompiling) return true;
    VirtualFile vFile = getVirtualFile();
    return vFile.isValid();
  }

  @NotNull
  public String getName() {
    return getVirtualFile().getName();
  }

  @NotNull
  public PsiElement[] getChildren() {
    return getClasses(); // TODO : package statement?
  }

  @NotNull
  public PsiClass[] getClasses() {
    final PsiClassHolderFileStub fileStub = getStub();
    return fileStub != null ? fileStub.getClasses() : PsiClass.EMPTY_ARRAY;
  }

  public PsiPackageStatement getPackageStatement() {
    getStub(); // Make sure myPackageStatement initializes.

    ClsPackageStatementImpl statement = myPackageStatement;
    if (statement == null) statement = new ClsPackageStatementImpl(this);
    return statement.getPackageName() != null ? statement : null;
  }

  @NotNull
  public String getPackageName() {
    PsiPackageStatement statement = getPackageStatement();
    return statement == null ? "" : statement.getPackageName();
  }

  public void setPackageName(final String packageName) throws IncorrectOperationException {
    throw new IncorrectOperationException("Cannot set package name for compiled files");
  }

  public PsiImportList getImportList() {
    return null;
  }

  public boolean importClass(PsiClass aClass) {
    throw new UnsupportedOperationException("Cannot add imports to compiled classes");
  }

  @NotNull
  public PsiElement[] getOnDemandImports(boolean includeImplicit, boolean checkIncludes) {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  @NotNull
  public PsiClass[] getSingleClassImports(boolean checkIncludes) {
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getImplicitlyImportedPackages() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public Set<String> getClassNames() {
    return Collections.singleton(getVirtualFile().getNameWithoutExtension());
  }

  @NotNull
  public PsiJavaCodeReferenceElement[] getImplicitlyImportedPackageReferences() {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  public PsiJavaCodeReferenceElement findImportReferenceTo(PsiClass aClass) {
    return null;
  }

  @NotNull
  public LanguageLevel getLanguageLevel() {
    final List stubs = getStub().getChildrenStubs();
    return stubs.size() > 0 ? ((PsiClassStub<?>)stubs.get(0)).getLanguageLevel() : LanguageLevel.HIGHEST;
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public void checkSetName(String name) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public boolean isDirectory() {
    return false;
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    buffer.append(PsiBundle.message("psi.decompiled.text.header"));
    goNextLine(indentLevel, buffer);
    goNextLine(indentLevel, buffer);
    final PsiPackageStatement packageStatement = getPackageStatement();
    if (packageStatement != null) {
      ((ClsElementImpl)packageStatement).appendMirrorText(0, buffer);
      goNextLine(indentLevel, buffer);
      goNextLine(indentLevel, buffer);
    }

    final PsiClass[] classes = getClasses();
    if (classes.length > 0) {
      PsiClass aClass = classes[0];
      ((ClsElementImpl)aClass).appendMirrorText(0, buffer);
    }
  }

  public void setMirror(@NotNull TreeElement element) {
    PsiElement mirrorFile = SourceTreeToPsiMap.treeElementToPsi(element);
    if (mirrorFile instanceof PsiJavaFile) {
      PsiPackageStatement packageStatementMirror = ((PsiJavaFile)mirrorFile).getPackageStatement();
      final PsiPackageStatement packageStatement = getPackageStatement();
      if (packageStatementMirror != null && packageStatement != null) {
        ((ClsElementImpl)packageStatement).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(packageStatementMirror));
      }

      PsiClass[] classes = getClasses();
      // Can happen for package-info.class, or classes compiled from languages, that support different class naming scheme, like Scala.
      if (classes.length != 1 || JavaPsiFacade.getInstance(getProject()).getNameHelper().isIdentifier(classes[0].getName())) {
        PsiClass[] mirrorClasses = ((PsiJavaFile)mirrorFile).getClasses();
        LOG.assertTrue(classes.length == mirrorClasses.length);
        if (classes.length == mirrorClasses.length) {
          for (int i = 0; i < classes.length; i++) {
            ((ClsElementImpl)classes[i]).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirrorClasses[i]));
          }
        }
      }
    }
    myMirrorFileElement = element;
  }

  @NotNull
  public PsiElement getNavigationElement() {
    String packageName = getPackageName();
    PsiClass[] classes = getClasses();
    if (classes.length == 0) return this;
    String sourceFileName = ((ClsClassImpl)classes[0]).getSourceFileName();
    String relativeFilePath = packageName.length() == 0 ? sourceFileName : packageName.replace('.', '/') + '/' + sourceFileName;

    final VirtualFile vFile = getContainingFile().getVirtualFile();
    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(getProject()).getFileIndex();
    final List<OrderEntry> orderEntries = projectFileIndex.getOrderEntriesForFile(vFile);
    for (OrderEntry orderEntry : orderEntries) {
      VirtualFile[] files = orderEntry.getFiles(OrderRootType.SOURCES);
      for (VirtualFile file : files) {
        VirtualFile source = file.findFileByRelativePath(relativeFilePath);
        if (source != null) {
          PsiFile psiSource = getManager().findFile(source);
          if (psiSource instanceof PsiClassOwner) {
            return psiSource;
          }
        }
      }
    }
    return this;
  }

  @Override
  public PsiElement getMirror() {
    synchronized (MIRROR_LOCK) {
      if (myMirrorFileElement == null) {
        FileDocumentManager documentManager = FileDocumentManager.getInstance();
        final Document document = documentManager.getDocument(getVirtualFile());
        String text = document.getText();
        String ext = StdFileTypes.JAVA.getDefaultExtension();
        PsiClass aClass = getClasses()[0];
        String fileName = aClass.getName() + "." + ext;
        PsiManager manager = getManager();
        PsiFile mirror = PsiFileFactory.getInstance(manager.getProject()).createFileFromText(fileName, text);
        final ASTNode mirrorTreeElement = SourceTreeToPsiMap.psiElementToTree(mirror);

        //IMPORTANT: do not take lock too early - FileDocumentManager.getInstance().saveToString() can run write action...
        ProgressManager.getInstance().executeNonCancelableSection(new Runnable() {
          public void run() {
            setMirror((TreeElement)mirrorTreeElement);
            myMirrorFileElement.putUserData(DOCUMENT_IN_MIRROR_KEY, document);
          }
        });
      }

      return myMirrorFileElement.getPsi();
    }
  }

  public long getModificationStamp() {
    return getVirtualFile().getModificationStamp();
  }

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

  @NotNull
  public PsiFile getOriginalFile() {
    return this;
  }

  @NotNull
  public FileType getFileType() {
    return StdFileTypes.CLASS;
  }

  @NotNull
  public PsiFile[] getPsiRoots() {
    return new PsiFile[]{this};
  }

  @NotNull
  public FileViewProvider getViewProvider() {
    return myViewProvider;
  }

  public void subtreeChanged() {
  }

  public static String decompile(PsiManager manager, VirtualFile file) {
    final FileViewProvider provider = ((PsiManagerEx)manager).getFileManager().findViewProvider(file);
    ClsFileImpl psiFile = null;
    if (provider != null) {
      final PsiFile psi = provider.getPsi(provider.getBaseLanguage());
      if (psi instanceof ClsFileImpl) {
        psiFile = (ClsFileImpl)psi;
      }
    }

    if (psiFile == null) {
      psiFile = new ClsFileImpl((PsiManagerImpl)manager, new ClassFileViewProvider(manager, file), true);
    }

    StringBuffer buffer = new StringBuffer();
    psiFile.appendMirrorText(0, buffer);
    return buffer.toString();
  }

  @Override
  public PsiElement getContext() {
    return FileContextUtil.getFileContext(this);
  }

  @NotNull
  public PsiClassHolderFileStub getStub() {
    return (PsiClassHolderFileStub)getStubTree().getRoot();
  }

  private final Object lock = new Object();

  @NotNull
  public StubTree getStubTree() {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final StubTree derefd = derefStub();
    if (derefd != null) return derefd;

    StubTree stubHolder = StubTree.readOrBuild(getProject(), getVirtualFile());
    if (stubHolder == null) {
      // Must be corrupted classfile
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
    synchronized (MIRROR_LOCK) {
      myMirrorFileElement = null;
      myPackageStatement = new ClsPackageStatementImpl(this);
    }
  }

  @Nullable
  private StubTree derefStub() {
    synchronized (lock) {
      return myStub != null ? myStub.get() : null;
    }
  }

  public ASTNode findTreeForStub(final StubTree tree, final StubElement<?> stub) {
    return null;
  }

  public boolean isContentsLoaded() {
    return myStub != null;
  }

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

  public PsiFile cacheCopy(final FileContent content) {
    return this;
  }

  public void putInfo(Map<String, String> info) {
    PsiFileImpl.putInfo(this, info);
  }
}
