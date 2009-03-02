package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStrongWhitespaceHolderElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class TreeUtil {
  public static final Key<String> UNCLOSED_ELEMENT_PROPERTY = Key.create("UNCLOSED_ELEMENT_PROPERTY");

  private TreeUtil() {
  }

  @Nullable
  public static ASTNode findChildBackward(ASTNode parent, IElementType type) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    for(ASTNode element = parent.getLastChildNode(); element != null; element = element.getTreePrev()){
      if (element.getElementType() == type) return element;
    }
    return null;
  }

  @Nullable
  public static ASTNode skipElements(ASTNode element, TokenSet types) {
    while(true){
      if (element == null) return null;
      if (!types.contains(element.getElementType())) break;
      element = element.getTreeNext();
    }
    return element;
  }

  @Nullable
  public static ASTNode skipElementsBack(ASTNode element, TokenSet types) {
    if (!types.contains(element.getElementType())) return element;

    ASTNode parent = element.getTreeParent();
    ASTNode prev = element;
    while (prev instanceof CompositeElement) {
      if (!types.contains(prev.getElementType())) return prev;
      prev = prev.getTreePrev();
    }
    if (prev == null) return null;
    ASTNode firstChildNode = parent.getFirstChildNode();
    ASTNode lastRelevant = null;
    while(firstChildNode != prev){
      if (!types.contains(firstChildNode.getElementType())) lastRelevant = firstChildNode;
      firstChildNode = firstChildNode.getTreeNext();
    }
    return lastRelevant;
  }

  @Nullable
  public static ASTNode findParent(ASTNode element, IElementType type) {
    for(ASTNode parent = element.getTreeParent(); parent != null; parent = parent.getTreeParent()){
      if (parent.getElementType() == type) return parent;
    }
    return null;
  }

  @Nullable
  public static LeafElement findFirstLeaf(ASTNode element) {
    if (element instanceof LeafElement){
      return (LeafElement)element;
    }
    else{
      for(ASTNode child = element.getFirstChildNode(); child != null; child = child.getTreeNext()){
        LeafElement leaf = findFirstLeaf(child);
        if (leaf != null) return leaf;
      }
      return null;
    }
  }

  public static boolean isLeafOrCollapsedChameleon(ASTNode node) {
    if (node instanceof LeafElement) return true;
    if (node instanceof LazyParseableElement && !((LazyParseableElement)node).isParsed()) return true;
    return false;
  }

  @Nullable
  public static TreeElement findFirstLeafOrChameleon(TreeElement element) {
    if (isLeafOrCollapsedChameleon(element)) {
      return element;
    }
    else{
      for(TreeElement child = element.getFirstChildNode(); child != null; child = child.getTreeNext()){
        TreeElement leaf = findFirstLeafOrChameleon(child);
        if (leaf != null) return leaf;
      }
      return null;
    }
  }

  @Nullable
  public static LeafElement findLastLeaf(ASTNode element) {
    if (element instanceof LeafElement){
      return (LeafElement)element;
    }
    else{
      for(ASTNode child = element.getLastChildNode(); child != null; child = child.getTreePrev()){
        LeafElement leaf = findLastLeaf(child);
        if (leaf != null) return leaf;
      }
      return null;
    }
  }

  @Nullable
  public static ASTNode findSibling(ASTNode start, IElementType elementType) {
    ASTNode child = start;
    while (true) {
      if (child == null) return null;
      if (child.getElementType() == elementType) return child;
      child = child.getTreeNext();
    }
  }

  @Nullable
  public static ASTNode findCommonParent(ASTNode one, ASTNode two){
    // optimization
    if(one == two) return one;
    final Set<ASTNode> parents = new HashSet<ASTNode>(20);
    while (one != null) {
      parents.add(one);
      one = one.getTreeParent();
    }
    while(two != null){
      if(parents.contains(two)) return two;
      two = two.getTreeParent();
    }
    return null;
  }

  public static void clearCaches(TreeElement tree) {
    tree.clearCaches();
    TreeElement child = tree.getFirstChildNode();
    while(child != null){
      clearCaches(child);
      child = child.getTreeNext();
    }
  }

  @Nullable
  public static ASTNode nextLeaf(@NotNull final ASTNode node) {
    return nextLeaf((TreeElement)node, null);
  }

  public static FileElement getFileElement(TreeElement parent) {
    while(parent != null && !(parent instanceof FileElement)) {
      parent = parent.getTreeParent();
    }
    return (FileElement)parent;
  }

  @Nullable
  public static ASTNode prevLeaf(final ASTNode node) {
    return prevLeaf((TreeElement)node, null);
  }

  public static boolean isStrongWhitespaceHolder(IElementType type) {
    return type instanceof IStrongWhitespaceHolderElementType;
  }

  public static String getTokenText(Lexer lexer) {
    return lexer.getBufferSequence().subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
  }

  @Nullable
  public static LeafElement nextLeaf(@NotNull TreeElement start, CommonParentState commonParent) {
    return (LeafElement)nextLeaf(start, commonParent, null, true);
  }

  @Nullable
  public static TreeElement nextLeaf(@NotNull TreeElement start, CommonParentState commonParent,
                                     IElementType searchedType,
                                     boolean expandChameleons) {
    if (commonParent != null) {
      commonParent.startLeafBranchStart = start;
      initStrongWhitespaceHolder(commonParent, start, true);
    }
    TreeElement nextTree = start;
    TreeElement next = null;
    while (next == null && (nextTree = nextTree.getTreeNext()) != null) {
      if (nextTree.getElementType() == searchedType) {
        return nextTree;
      }
      next = findFirstLeaf(nextTree, searchedType, commonParent, expandChameleons);
    }
    if (next != null) {
      if (commonParent != null) commonParent.nextLeafBranchStart = nextTree;
      return next;
    }
    final CompositeElement parent = start.getTreeParent();
    if (parent == null) return null;
    return nextLeaf(parent, commonParent, searchedType, expandChameleons);
  }

  private static void initStrongWhitespaceHolder(CommonParentState commonParent, ASTNode start, boolean slopeSide) {
    if (start instanceof CompositeElement &&
        (isStrongWhitespaceHolder(start.getElementType()) || slopeSide && start.getUserData(UNCLOSED_ELEMENT_PROPERTY) != null)) {
      commonParent.strongWhiteSpaceHolder = (CompositeElement)start;
      commonParent.isStrongElementOnRisingSlope = slopeSide;
    }
  }

  @Nullable
  private static TreeElement findFirstLeaf(TreeElement element, 
                                           IElementType searchedType,
                                           CommonParentState commonParent,
                                           boolean expandChameleons) {
    if (commonParent != null) {
      initStrongWhitespaceHolder(commonParent, element, false);
    }

    if (!expandChameleons && isLeafOrCollapsedChameleon(element)) {
      return element;
    }

    if (element instanceof LeafElement || element.getElementType() == searchedType) {
      return element;
    }
    else {
      for (TreeElement child = element.getFirstChildNode(); child != null; child = child.getTreeNext()) {
        TreeElement leaf = findFirstLeaf(child, searchedType, commonParent, expandChameleons);
        if (leaf != null) return leaf;
      }
      return null;
    }
  }

  @Nullable
  public static LeafElement prevLeaf(TreeElement start, @Nullable CommonParentState commonParent) {
    if (start == null) return null;
    if (commonParent != null) {
      if (commonParent.strongWhiteSpaceHolder != null && start.getUserData(UNCLOSED_ELEMENT_PROPERTY) != null) {
        commonParent.strongWhiteSpaceHolder = (CompositeElement)start;
      }
      commonParent.nextLeafBranchStart = start;
    }
    ASTNode prevTree = start;
    LeafElement prev = null;
    while (prev == null && (prevTree = prevTree.getTreePrev()) != null) {
      prev = findLastLeaf(prevTree);
    }
    if (prev != null) {
      if (commonParent != null) commonParent.startLeafBranchStart = (TreeElement)prevTree;
      return prev;
    }
    final CompositeElement parent = start.getTreeParent();
    if (parent == null) return null;
    return prevLeaf(parent, commonParent);
  }

  @Nullable
  public static ASTNode getLastChild(final ASTNode element) {
    if (element == null) {
      return null;
    }
    if (element instanceof LeafElement) {
      return element;
    }
    else {
      final ASTNode node = element.getLastChildNode();
      if (node instanceof LeafElement) ChameleonTransforming.transform((LeafElement)node);
      final ASTNode lastChild = element.getLastChildNode();
      if (lastChild == null) {
        return element;
      }
      else {
        return getLastChild(lastChild);
      }
    }
  }

  public static final class CommonParentState {
    public TreeElement startLeafBranchStart = null;
    public ASTNode nextLeafBranchStart = null;
    public CompositeElement strongWhiteSpaceHolder = null;
    public boolean isStrongElementOnRisingSlope = true;
  }
}
