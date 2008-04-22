package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;

public class FileElement extends CompositeElement{
  private CharTable myCharTable = new CharTableImpl();
  private PsiManagerEx myManager;

  public CharTable getCharTable() {
    return myCharTable;
  }

  public FileElement(IElementType type) {
    super(type);
  }

  public void setManager(final PsiManagerEx manager) {
    myManager = manager;
  }

  public PsiManagerEx getManager() {
    if (myManager == null) {
      if(parent != null) return parent.getManager();
      return (PsiManagerEx)SourceTreeToPsiMap.treeElementToPsi(this).getManager(); //TODO: cache?
    }
    return myManager;
  }

  public ASTNode copyElement() {
    PsiFileImpl psiElement = (PsiFileImpl)getPsi();
    PsiFileImpl psiElementCopy = (PsiFileImpl)psiElement.copy();
    return psiElementCopy.getTreeElement();
  }

  public void setCharTable(CharTable table) {
    myCharTable = table;
  }
}
