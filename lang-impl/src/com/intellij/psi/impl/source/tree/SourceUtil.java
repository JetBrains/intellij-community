package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
public class SourceUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.SourceUtil");

  private SourceUtil() {
  }

  public static int toBuffer(ASTNode element, char[] buffer, int offset) {
    return toBuffer(element, buffer, offset, null);
  }

  private static int toBuffer(ASTNode element, char[] buffer, int offset, TokenSet skipTypes) {
    if (skipTypes != null && skipTypes.contains(element.getElementType())) return offset;
    if (element instanceof LeafElement) {
      return ((LeafElement)element).copyTo(buffer, offset);
    }
    int curOffset = offset;
    for (TreeElement child = (TreeElement)element.getFirstChildNode(); child != null; child = child.next) {
      curOffset = toBuffer(child, buffer, curOffset, skipTypes);
    }
    return curOffset;
  }

  public static String getTextSkipWhiteSpaceAndComments(ASTNode element) {
    int length = toBuffer(element, null, 0, StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
    char[] buffer = new char[length];
    toBuffer(element, buffer, 0, StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
    return new String(buffer);
  }

  public static TreeElement addParenthToReplacedChild(final IElementType parenthType,
                                                      TreeElement newChild,
                                                      PsiManager manager) {
    CompositeElement parenthExpr = Factory.createCompositeElement(parenthType);

    TreeElement dummyExpr = (TreeElement)newChild.clone();
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(newChild);
    new DummyHolder(manager, parenthExpr, null, charTableByTree);
    parenthExpr.putUserData(CharTable.CHAR_TABLE_KEY, charTableByTree);
    TreeUtil.addChildren(parenthExpr, Factory.createLeafElement(JavaTokenType.LPARENTH, "(", 0, 1, charTableByTree));
    TreeUtil.addChildren(parenthExpr, dummyExpr);
    TreeUtil.addChildren(parenthExpr, Factory.createLeafElement(JavaTokenType.RPARENTH, ")", 0, 1, charTableByTree));

    try {
      CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
      parenthExpr =
      (CompositeElement)SourceTreeToPsiMap.psiElementToTree(
        codeStyleManager.reformat(SourceTreeToPsiMap.treeElementToPsi(parenthExpr)));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e); // should not happen
    }

    newChild.putUserData(CharTable.CHAR_TABLE_KEY, SharedImplUtil.findCharTableByTree(newChild));
    TreeUtil.replaceWithList(dummyExpr, newChild);

    newChild = parenthExpr;
    // TODO remove explicit caches drop since this should be ok iff we will use ChangeUtil for the modification 
    TreeUtil.clearCaches(TreeUtil.getFileElement(newChild));
    return newChild;
  }

  public static void dequalifyImpl(@NotNull CompositeElement reference) {
    final ASTNode qualifier = reference.findChildByRole(ChildRole.QUALIFIER);
    if (qualifier != null) {
      reference.deleteChildInternal(qualifier);
    }
  }
}
