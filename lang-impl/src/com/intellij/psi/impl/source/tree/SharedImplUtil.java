package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

//TODO: rename/regroup?

public class SharedImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.SharedImplUtil");

  private SharedImplUtil() {
  }

  public static PsiElement getParent(ASTNode thisElement) {
    return SourceTreeToPsiMap.treeElementToPsi(thisElement.getTreeParent());
  }

  public static PsiElement getFirstChild(ASTNode element) {
    final TreeElement firstChild = (TreeElement)element.getFirstChildNode();
    if (firstChild == null) return null;
    ASTNode transformed = firstChild.getTransformedFirstOrSelf();
    if (transformed == null) {
      transformed = element.getFirstChildNode();
    }
    return SourceTreeToPsiMap.treeElementToPsi(transformed);
  }

  @Nullable
  public static PsiElement getLastChild(ASTNode element) {
    final TreeElement lastChild = (TreeElement)element.getLastChildNode();
    if (lastChild == null) return null;
    final ASTNode last = lastChild.getTransformedLastOrSelf(); // this may be null on a zero-length file
    return last == null ? null : last.getPsi();
  }

  public static PsiElement getNextSibling(ASTNode thisElement) {
    final TreeElement treeNext = ((TreeElement)thisElement).next;
    if (treeNext == null) return null;
    ASTNode transformed = treeNext.getTransformedFirstOrSelf();
    if (transformed == null) {
      transformed = ((TreeElement)thisElement).next;
    }
    return SourceTreeToPsiMap.treeElementToPsi(transformed);
  }

  public static PsiElement getPrevSibling(ASTNode thisElement) {
    final TreeElement treePrev = ((TreeElement)thisElement).prev;
    return treePrev == null ? null : SourceTreeToPsiMap.treeElementToPsi(treePrev.getTransformedLastOrSelf());
  }

  public static PsiFile getContainingFile(ASTNode thisElement) {
    TreeElement element;
    for (element = (TreeElement)thisElement; element.parent != null; element = element.parent) {
    }

    PsiElement psiElement = element.getPsi();
    if (!(psiElement instanceof PsiFile)) return null;
    return psiElement.getContainingFile();
  }

  public static boolean isValid(ASTNode thisElement) {
    LOG.assertTrue(thisElement instanceof PsiElement);
    PsiFile file = getContainingFile(thisElement);
    return file != null && file.isValid();
  }

  public static boolean isWritable(ASTNode thisElement) {
    PsiFile file = SourceTreeToPsiMap.treeElementToPsi(thisElement).getContainingFile();
    return file == null || file.isWritable();
  }

  public static CharTable findCharTableByTree(ASTNode tree) {
    while (tree != null) {
      final CharTable userData = tree.getUserData(CharTable.CHAR_TABLE_KEY);
      if (userData != null) return userData;
      if (tree instanceof FileElement) return ((FileElement)tree).getCharTable();
      tree = tree.getTreeParent();
    }
    LOG.assertTrue(false, "Invalid root element");
    return null;
  }

  public static PsiElement addRange(PsiElement thisElement,
                                    PsiElement first,
                                    PsiElement last,
                                    ASTNode anchor,
                                    Boolean before) throws IncorrectOperationException {
    CheckUtil.checkWritable(thisElement);
    final CharTable table = findCharTableByTree(SourceTreeToPsiMap.psiElementToTree(thisElement));

    TreeElement copyFirst = null;
    ASTNode copyLast = null;
    ASTNode next = SourceTreeToPsiMap.psiElementToTree(last).getTreeNext();
    ASTNode parent = null;
    for (ASTNode element = SourceTreeToPsiMap.psiElementToTree(first); element != next; element = element.getTreeNext()) {
      TreeElement elementCopy = ChangeUtil.copyElement((TreeElement)element, table);
      if (element == first.getNode()) {
        copyFirst = elementCopy;
      }
      if (element == last.getNode()) {
        copyLast = elementCopy;
      }
      if (parent == null) {
        parent = elementCopy.getTreeParent();
      }
      else {
        if(elementCopy.getElementType() == TokenType.WHITE_SPACE)
          CodeEditUtil.setNodeGenerated(elementCopy, true);
        parent.addChild(elementCopy, null);
      }
    }
    if (copyFirst == null) return null;
    copyFirst = ((CompositeElement)SourceTreeToPsiMap.psiElementToTree(thisElement)).addInternal(copyFirst, copyLast, anchor, before);
    for (TreeElement element = copyFirst; element != null; element = element.getTreeNext()) {
      element = ChangeUtil.decodeInformation(element);
      if (element.getTreePrev() == null) {
        copyFirst = element;
      }
    }
    return SourceTreeToPsiMap.treeElementToPsi(copyFirst);
  }

  public static PsiManager getManagerByTree(final ASTNode node) {
    if(node instanceof FileElement) return node.getPsi().getManager();
    return node.getTreeParent().getPsi().getManager();
  }

  public static ASTNode[] getChildrenOfType(ASTNode node, IElementType elementType) {
    int count = countChildrenOfType(node, elementType);
    if (count == 0) {
      return ASTNode.EMPTY_ARRAY;
    }
    final ASTNode[] result = new ASTNode[count];
    count = 0;
    for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getElementType() == elementType) {
        result[count++] = child;
      }
    }
    return result;
  }

  private static int countChildrenOfType(@NotNull ASTNode node, @NotNull IElementType elementType) {
    ChameleonTransforming.transformChildren(node);

    // no lock is needed because all chameleons are expanded already
    int count = 0;
    for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getElementType() == elementType) {
        count++;
      }
    }

    return count;
  }
}
