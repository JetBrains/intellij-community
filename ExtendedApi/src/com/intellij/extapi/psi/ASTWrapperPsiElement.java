package com.intellij.extapi.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChameleonElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class ASTWrapperPsiElement extends PsiElementBase {
  private ASTNode myNode;

  public ASTWrapperPsiElement(@NotNull final ASTNode node) {
    myNode = node;
  }

  public PsiManager getManager() {
    final PsiElement parent = getParent();
    if (parent == null) throw new PsiInvalidElementAccessException(this);
    return parent.getManager();
  }

  @NotNull
  public PsiElement[] getChildren() {
    final PsiElement psiChild = getFirstChild();
    if (psiChild == null) return EMPTY_ARRAY;

    List<PsiElement> result = new ArrayList<PsiElement>();
    ASTNode childNode = psiChild.getNode();
    while (childNode != null) {
      if (childNode instanceof ChameleonElement) {
        childNode = ((TreeElement)childNode).getTransformedFirstOrSelf();
        if (childNode == null) break;
      }

      if (childNode instanceof CompositeElement) {
        result.add(childNode.getPsi());
      }
      childNode = childNode.getTreeNext();
    }
    return result.toArray(new PsiElement[result.size()]);
  }

  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
    final PsiElement psiChild = getFirstChild();
    if (psiChild == null) return;

    ASTNode childNode = psiChild.getNode();
    while (childNode != null) {
      if (childNode instanceof ChameleonElement) {
        childNode = ((TreeElement)childNode).getTransformedFirstOrSelf();
        if (childNode == null) break;
      }

      childNode.getPsi().accept(visitor);
      childNode = childNode.getTreeNext();
    }
  }

  public PsiElement getParent() {
    return SharedImplUtil.getParent(myNode);
  }

  public PsiElement getFirstChild() {
    return SharedImplUtil.getFirstChild(myNode);
  }

  public PsiElement getLastChild() {
    return SharedImplUtil.getLastChild(myNode);
  }

  public PsiElement getNextSibling() {
    return SharedImplUtil.getNextSibling(myNode);
  }

  public PsiElement getPrevSibling() {
    return SharedImplUtil.getPrevSibling(myNode);
  }

  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

  public int getStartOffsetInParent() {
    return myNode.getStartOffset() - myNode.getTreeParent().getStartOffset();
  }

  public int getTextLength() {
    return myNode.getTextLength();
  }

  public PsiElement findElementAt(int offset) {
    ASTNode treeElement = myNode.findLeafElementAt(offset);
    return SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  public int getTextOffset() {
    return myNode.getStartOffset();
  }

  public String getText() {
    return myNode.getText();
  }

  @NotNull
  public char[] textToCharArray() {
    return myNode.getText().toCharArray();
  }

  public boolean textContains(char c) {
    return myNode.textContains(c);
  }

  public <T> T getCopyableUserData(Key<T> key) {
    return myNode.getCopyableUserData(key);
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    myNode.putCopyableUserData(key, value);
  }

  @NotNull
  public ASTNode getNode() {
    return myNode;
  }

  public void subtreeChanged() {
  }

  @NotNull
  public Language getLanguage() {
    return myNode.getElementType().getLanguage();
  }

  @Nullable
  protected PsiElement findChildByType(IElementType type) {
    ASTNode node = getNode().findChildByType(type);
    return node == null ? null : node.getPsi();
  }

  protected PsiElement findChildByType(TokenSet type) {
    ASTNode node = com.intellij.psi.impl.source.tree.TreeUtil.findChild(getNode(), type);
    return node == null ? null : node.getPsi();
  }

  @Nullable
  protected PsiElement findChildByFilter(TokenSet tokenSet) {
    ASTNode[] nodes = getNode().getChildren(tokenSet);
    return nodes == null || nodes.length == 0 ? null : nodes[0].getPsi();
  }

  protected PsiElement[] findChildrenByType(IElementType elementType, Class<? extends PsiElement> arrayClass) {
    return ContainerUtil.map2Array(getNode().getChildren(TokenSet.create(elementType)), arrayClass, new Function<ASTNode, PsiElement>() {
      public PsiElement fun(final ASTNode s) {
        return s.getPsi();
      }
    });
  }

}
