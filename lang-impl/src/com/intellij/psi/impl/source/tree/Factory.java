package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CharTable;

/**
 *
 */
public class Factory  {
  private Factory() {}

  public static LeafElement createSingleLeafElement(IElementType type, CharSequence buffer, int startOffset, int endOffset, CharTable table, PsiManager manager, PsiFile originalFile) {
    final LeafElement newElement;
    final DummyHolder dummyHolder = DummyHolderFactory.createHolder(manager, table, type.getLanguage());
    dummyHolder.setOriginalFile(originalFile);
    dummyHolder.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, PsiUtil.getLanguageLevel(originalFile));
    final FileElement holderElement = dummyHolder.getTreeElement();
    newElement = ASTFactory.leaf(type, buffer, startOffset, endOffset, holderElement.getCharTable());
    TreeUtil.addChildren(holderElement, newElement);
    CodeEditUtil.setNodeGenerated(newElement, true);
    return newElement;
  }

  public static LeafElement createSingleLeafElement(IElementType type, CharSequence buffer, int startOffset, int endOffset, CharTable table, PsiManager manager, boolean generatedFlag) {
    final LeafElement newElement;
    final FileElement holderElement = DummyHolderFactory.createHolder(manager, table, type.getLanguage()).getTreeElementNoLock();
    newElement = ASTFactory.leaf(type, buffer, startOffset, endOffset, holderElement.getCharTable());
    TreeUtil.addChildren(holderElement, newElement);
    if(generatedFlag) CodeEditUtil.setNodeGenerated(newElement, true);
    return newElement;
  }

  public static LeafElement createSingleLeafElement(IElementType type, CharSequence buffer, int startOffset, int endOffset, CharTable table, PsiManager manager) {
    return createSingleLeafElement(type, buffer, startOffset, endOffset, table, manager, true);
  }

  public static CompositeElement createErrorElement(String description) {
    PsiErrorElementImpl errorElement = new PsiErrorElementImpl();
    errorElement.setErrorDescription(description);
    return errorElement;
  }

  public static CompositeElement createCompositeElement(final IElementType type,
                                                        final CharTable charTableByTree,
                                                        final PsiManager manager) {
    final FileElement treeElement = DummyHolderFactory.createHolder(manager, null, charTableByTree).getTreeElement();
    final CompositeElement composite = ASTFactory.composite(type);
    TreeUtil.addChildren(treeElement, composite);
    return composite;
  }
}