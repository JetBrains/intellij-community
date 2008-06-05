package com.intellij.psi.impl.source;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class DummyHolder extends PsiFileImpl {
  protected final PsiElement myContext;
  private final CharTable myTable;
  private final Boolean myExplicitlyValid;
  private final Language myLanguage;
  private volatile FileElement myFileElement = null;

  public DummyHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context) {
    this(manager, contentElement, context, SharedImplUtil.findCharTableByTree(contentElement));
  }

  public DummyHolder(@NotNull PsiManager manager, CharTable table, boolean validity) {
    this(manager, null, null, table, Boolean.valueOf(validity), FileTypes.PLAIN_TEXT.getLanguage());
  }

  public DummyHolder(@NotNull PsiManager manager, PsiElement context) {
    this(manager, null, context, null);
  }

  public DummyHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context, CharTable table) {
    this(manager, contentElement, context, table, null, context == null ? FileTypes.PLAIN_TEXT.getLanguage() : context.getLanguage());
  }

  public DummyHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context, CharTable table, Boolean validity, Language language) {
    super(TokenType.DUMMY_HOLDER, TokenType.DUMMY_HOLDER, new DummyHolderViewProvider(manager));
    ((DummyHolderViewProvider)getViewProvider()).setDummyHolder(this);
    myContext = context;
    myTable = table;
    if(contentElement != null) {
      TreeUtil.addChildren(getTreeElement(), contentElement);
      clearCaches();
    }
    myExplicitlyValid = validity;
    myLanguage = language;
  }

  public DummyHolder(@NotNull PsiManager manager, PsiElement context, CharTable table) {
    this(manager, null, context, table);
  }

  public DummyHolder(@NotNull PsiManager manager, final CharTable table, final Language language) {
    this(manager, null, null, table, null, language);
  }

  public PsiElement getContext() {
    return myContext;
  }

  public boolean isValid() {
    if(myExplicitlyValid != null) return myExplicitlyValid.booleanValue();
    return super.isValid() && !(myContext != null && !myContext.isValid());
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitFile(this);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
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
      myFileElement = new FileElement(TokenType.DUMMY_HOLDER);
      myFileElement.setPsi(this);
      if(myTable != null) myFileElement.setCharTable(myTable);
      clearCaches();
    }
    return myFileElement;
  }

  @NotNull
  public Language getLanguage() {
    return myLanguage;
  }

  @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException"})
  protected PsiFileImpl clone() {
    final PsiFileImpl psiFile = cloneImpl(myFileElement);
    final DummyHolderViewProvider dummyHolderViewProvider = new DummyHolderViewProvider(getManager());
    myViewProvider = dummyHolderViewProvider;
    dummyHolderViewProvider.setDummyHolder((DummyHolder)psiFile);
    final FileElement treeClone = (FileElement)calcTreeElement().clone();
    psiFile.setTreeElementPointer(treeClone); // should not use setTreeElement here because cloned file still have VirtualFile (SCR17963)
    if(isPhysical()) psiFile.myOriginalFile = this;
    else psiFile.myOriginalFile = myOriginalFile;
    treeClone.setPsi(psiFile);
    return psiFile;
  }

  private FileViewProvider myViewProvider = null;

  @NotNull
  public FileViewProvider getViewProvider() {
    if(myViewProvider != null) return myViewProvider;
    return super.getViewProvider();
  }
}
