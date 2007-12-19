package com.intellij.psi.impl.source;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class DummyHolder extends PsiFileImpl {
  protected PsiElement myContext;
  private CharTable myTable = null;
  private Boolean myExplicitlyValid = null;
  private Language myLanguage = StdLanguages.JAVA;

  private FileElement myFileElement = null;

  public DummyHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context) {
    this(manager, contentElement, context, SharedImplUtil.findCharTableByTree(contentElement));
  }

  public DummyHolder(@NotNull PsiManager manager, CharTable table, boolean validity) {
    this(manager, null, null, table);
    myExplicitlyValid = Boolean.valueOf(validity);
  }

  public DummyHolder(@NotNull PsiManager manager, PsiElement context) {
    super(DUMMY_HOLDER, DUMMY_HOLDER, new DummyHolderViewProvider(manager));
    ((DummyHolderViewProvider)getViewProvider()).setDummyHolder(this);
    myContext = context;
    if (context != null) {
      myLanguage = context.getLanguage();
    }
  }

  public DummyHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context, CharTable table) {
    this(manager, context);
    myContext = context;
    myTable = table;
    if(contentElement != null) {
      TreeUtil.addChildren(getTreeElement(), contentElement);
      clearCaches();
    }
  }

  public DummyHolder(@NotNull PsiManager manager, PsiElement context, CharTable table) {
    this(manager, context);
    myTable = table;
  }

  public DummyHolder(@NotNull PsiManager manager, final CharTable table, final Language language) {
    this(manager, null, table);
    myLanguage = language;
  }


  public PsiElement getContext() {
    return myContext;
  }

  public boolean isValid() {
    if(myExplicitlyValid != null) return myExplicitlyValid.booleanValue();
    if (!super.isValid()) return false;
    return !(myContext != null && !myContext.isValid());
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitFile(this);
  }

  public String toString() {
    return "DummyHolder";
  }

  @NotNull
  public FileType getFileType() {
    if (myContext != null) {
      PsiFile containingFile = myContext.getContainingFile();
      if (containingFile != null) return containingFile.getFileType();
    }
    return StdFileTypes.JAVA;
  }

  public FileElement getTreeElement() {
    if (myFileElement != null) return myFileElement;

    synchronized (PsiLock.LOCK) {
      return getTreeElementNoLock();
    }
  }

  public FileElement getTreeElementNoLock() {
    if(myFileElement == null){
      myFileElement = new FileElement(DUMMY_HOLDER);
      myFileElement.setPsiElement(this);
      if(myTable != null) myFileElement.setCharTable(myTable);
      clearCaches();
    }
    return myFileElement;
  }

  @NotNull
  public Language getLanguage() {
    return myLanguage;
  }

  public void setLanguage(final Language language) {
    myLanguage = language;
  }

  @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException"})
  protected PsiFileImpl clone() {
    final PsiFileImpl psiFile = (PsiFileImpl)cloneImpl(myFileElement);
    final DummyHolderViewProvider dummyHolderViewProvider = new DummyHolderViewProvider(getManager());
    myViewProvider = dummyHolderViewProvider;
    dummyHolderViewProvider.setDummyHolder((DummyHolder)psiFile);
    final FileElement treeClone = (FileElement)calcTreeElement().clone();
    psiFile.myTreeElementPointer = treeClone; // should not use setTreeElement here because cloned file still have VirtualFile (SCR17963)
    if(isPhysical()) psiFile.myOriginalFile = this;
    else psiFile.myOriginalFile = myOriginalFile;
    treeClone.setPsiElement(psiFile);
    return psiFile;
  }

  private FileViewProvider myViewProvider = null;

  @NotNull
  public FileViewProvider getViewProvider() {
    if(myViewProvider != null) return myViewProvider;
    return super.getViewProvider();
  }
}
