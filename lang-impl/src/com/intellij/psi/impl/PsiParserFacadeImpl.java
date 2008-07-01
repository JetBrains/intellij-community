package com.intellij.psi.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.util.IncorrectOperationException;
import com.intellij.lang.ASTFactory;

/**
 * @author yole
 */
public class PsiParserFacadeImpl implements PsiParserFacade {
  protected final PsiManagerEx myManager;

  public PsiParserFacadeImpl(PsiManagerEx manager) {
    myManager = manager;
  }

  @NotNull
  public PsiElement createWhiteSpaceFromText(@NotNull @NonNls String text) throws IncorrectOperationException {
    final FileElement holderElement = DummyHolderFactory.createHolder(myManager, null).getTreeElement();
    final LeafElement newElement = ASTFactory.leaf(TokenType.WHITE_SPACE, text, 0, text.length(), holderElement.getCharTable());
    TreeUtil.addChildren(holderElement, newElement);
    return newElement.getPsi();
  }
}
