package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;

public class FileElement extends LazyParseableElement {
  private volatile CharTable myCharTable = new CharTableImpl();

  public CharTable getCharTable() {
    return myCharTable;
  }

  public FileElement(IElementType type, CharSequence text) {
    super(type, text);
  }

  @Deprecated  // for 8.1 API compatibility
  public FileElement(IElementType type) {
    super(type, null);
  }

  public PsiManagerEx getManager() {
    if (getTreeParent() != null) return getTreeParent().getManager();
    return (PsiManagerEx)getPsi().getManager(); //TODO: cache?
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
