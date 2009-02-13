package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
public class Factory  {
  private Factory() {}

  public static LeafElement createSingleLeafElement(IElementType type, CharSequence buffer, int startOffset, int endOffset, CharTable table, PsiManager manager, PsiFile originalFile) {
    DummyHolder dummyHolder = DummyHolderFactory.createHolder(manager, table, type.getLanguage());
    dummyHolder.setOriginalFile(originalFile);

    FileElement holderElement = dummyHolder.getTreeElement();
    
    LeafElement newElement = ASTFactory.leaf(type, holderElement.getCharTable().intern(
      buffer, startOffset, endOffset));
    holderElement.rawAddChildren(newElement);
    CodeEditUtil.setNodeGenerated(newElement, true);
    return newElement;
  }

  public static LeafElement createSingleLeafElement(IElementType type, CharSequence buffer, int startOffset, int endOffset, CharTable table, PsiManager manager, boolean generatedFlag) {
    final FileElement holderElement = DummyHolderFactory.createHolder(manager, table, type.getLanguage()).getTreeElementNoLock();
    final LeafElement newElement = ASTFactory.leaf(type, holderElement.getCharTable().intern(
      buffer, startOffset, endOffset));
    holderElement.rawAddChildren(newElement);
    if(generatedFlag) CodeEditUtil.setNodeGenerated(newElement, true);
    return newElement;
  }

  public static LeafElement createSingleLeafElement(IElementType type, CharSequence buffer, CharTable table, PsiManager manager) {
    return createSingleLeafElement(type, buffer, 0, buffer.length(), table, manager);
  }

  public static LeafElement createSingleLeafElement(IElementType type, CharSequence buffer, int startOffset, int endOffset, CharTable table, PsiManager manager) {
    return createSingleLeafElement(type, buffer, startOffset, endOffset, table, manager, true);
  }

  public static CompositeElement createErrorElement(String description) {
    PsiErrorElementImpl errorElement = new PsiErrorElementImpl();
    errorElement.setErrorDescription(description);
    return errorElement;
  }

  @NotNull
  public static CompositeElement createCompositeElement(final IElementType type,
                                                        final CharTable charTableByTree,
                                                        final PsiManager manager) {
    final FileElement treeElement = DummyHolderFactory.createHolder(manager, null, charTableByTree).getTreeElement();
    final CompositeElement composite = ASTFactory.composite(type);
    treeElement.rawAddChildren(composite);
    return composite;
  }
}
