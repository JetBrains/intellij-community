// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStrongWhitespaceHolderElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TreeUtil {
  private static final Key<String> UNCLOSED_ELEMENT_PROPERTY = Key.create("UNCLOSED_ELEMENT_PROPERTY");

  public static void ensureParsed(@Nullable ASTNode node) {
    if (node != null) {
      node.getFirstChildNode();
    }
  }

  public static boolean isCollapsedChameleon(ASTNode node) {
    return node instanceof LazyParseableElement && !((LazyParseableElement)node).isParsed();
  }

  public static @Nullable ASTNode findChildBackward(@NotNull ASTNode parent, @NotNull IElementType type) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED && parent instanceof TreeElement) {
      ((TreeElement)parent).assertReadAccessAllowed();
    }
    for (ASTNode element = parent.getLastChildNode(); element != null; element = element.getTreePrev()) {
      if (element.getElementType() == type) return element;
    }
    return null;
  }

  public static @Nullable ASTNode skipElements(@Nullable ASTNode element, @NotNull TokenSet types) {
    ASTNode candidate = element;
    while (candidate != null && types.contains(candidate.getElementType())) candidate = candidate.getTreeNext();
    return candidate;
  }

  public static @Nullable ASTNode skipElementsBack(@Nullable ASTNode element, @NotNull TokenSet types) {
    ASTNode candidate = element;
    while (candidate != null && types.contains(candidate.getElementType())) candidate = candidate.getTreePrev();
    return candidate;
  }

  public static @Nullable ASTNode findParent(@NotNull ASTNode element, @NotNull IElementType type) {
    for (ASTNode parent = element.getTreeParent(); parent != null; parent = parent.getTreeParent()) {
      if (parent.getElementType() == type) return parent;
    }
    return null;
  }

  public static @Nullable ASTNode findParent(@NotNull ASTNode element, @NotNull TokenSet types) {
    for (ASTNode parent = element.getTreeParent(); parent != null; parent = parent.getTreeParent()) {
      if (types.contains(parent.getElementType())) return parent;
    }
    return null;
  }

  public static @Nullable ASTNode findParent(@NotNull ASTNode element, @NotNull TokenSet types, @Nullable TokenSet stopAt) {
    for (ASTNode parent = element.getTreeParent(); parent != null; parent = parent.getTreeParent()) {
      if (types.contains(parent.getElementType())) return parent;
      if (stopAt != null && stopAt.contains(parent.getElementType())) return null;
    }
    return null;
  }

  public static @Nullable LeafElement findFirstLeaf(@NotNull ASTNode element) {
    return (LeafElement)findFirstLeaf(element, true);
  }

  public static ASTNode findFirstLeaf(@NotNull ASTNode element, boolean expandChameleons) {
    if (element instanceof LeafElement || !expandChameleons && isCollapsedChameleon(element)) {
      return element;
    }
    else {
      for (ASTNode child = element.getFirstChildNode(); child != null; child = child.getTreeNext()) {
        ASTNode leaf = findFirstLeaf(child, expandChameleons);
        if (leaf != null) return leaf;
      }
      return null;
    }
  }

  public static @Nullable ASTNode findLastLeaf(@NotNull ASTNode element) {
    return findLastLeaf(element, true);
  }

  public static ASTNode findLastLeaf(@NotNull ASTNode element, boolean expandChameleons) {
    if (element instanceof LeafElement || !expandChameleons && isCollapsedChameleon(element)) {
      return element;
    }
    for (ASTNode child = element.getLastChildNode(); child != null; child = child.getTreePrev()) {
      ASTNode leaf = findLastLeaf(child);
      if (leaf != null) return leaf;
    }
    return null;
  }

  public static @Nullable ASTNode findSibling(ASTNode start, @NotNull IElementType elementType) {
    ASTNode child = start;
    while (true) {
      if (child == null) return null;
      if (child.getElementType() == elementType) return child;
      child = child.getTreeNext();
    }
  }

  public static @Nullable ASTNode findSibling(ASTNode start, @NotNull TokenSet types) {
    ASTNode child = start;
    while (true) {
      if (child == null) return null;
      if (types.contains(child.getElementType())) return child;
      child = child.getTreeNext();
    }
  }

  public static @Nullable ASTNode findSiblingBackward(ASTNode start, @NotNull IElementType elementType) {
    ASTNode child = start;
    while (true) {
      if (child == null) return null;
      if (child.getElementType() == elementType) return child;
      child = child.getTreePrev();
    }
  }


  public static @Nullable ASTNode findSiblingBackward(ASTNode start, @NotNull TokenSet types) {
    ASTNode child = start;
    while (true) {
      if (child == null) return null;
      if (types.contains(child.getElementType())) return child;
      child = child.getTreePrev();
    }
  }

  public static @Nullable ASTNode findCommonParent(ASTNode one, ASTNode two) {
    // optimization
    if (one == two) return one;
    Set<ASTNode> parents = new HashSet<>(20);
    while (one != null) {
      parents.add(one);
      one = one.getTreeParent();
    }
    while (two != null) {
      if (parents.contains(two)) return two;
      two = two.getTreeParent();
    }
    return null;
  }

  public static @NotNull Couple<ASTNode> findTopmostSiblingParents(ASTNode one, ASTNode two) {
    if (one == two) return Couple.of(null, null);

    LinkedList<ASTNode> oneParents = new LinkedList<>();
    while (one != null) {
      oneParents.add(one);
      one = one.getTreeParent();
    }
    LinkedList<ASTNode> twoParents = new LinkedList<>();
    while (two != null) {
      twoParents.add(two);
      two = two.getTreeParent();
    }

    do {
      one = oneParents.pollLast();
      two = twoParents.pollLast();
    }
    while (one == two && one != null);

    return Couple.of(one, two);
  }

  public static void clearCaches(@NotNull TreeElement tree) {
    tree.acceptTree(new RecursiveTreeElementWalkingVisitor(false) {
      @Override
      protected void visitNode(TreeElement element) {
        element.clearCaches();
        super.visitNode(element);
      }
    });
  }

  public static @Nullable ASTNode nextLeaf(@NotNull ASTNode node) {
    return nextLeaf((TreeElement)node, null);
  }

  public static @Nullable LeafElement nextLeaf(@NotNull LeafElement node) {
    return nextLeaf(node, null);
  }

  public static final Key<FileElement> CONTAINING_FILE_KEY_AFTER_REPARSE = Key.create("CONTAINING_FILE_KEY_AFTER_REPARSE");
  public static FileElement getFileElement(@NotNull TreeElement element) {
    TreeElement parent = element;
    while (parent != null && !(parent instanceof FileElement)) {
      parent = parent.getTreeParent();
    }
    if (parent == null) {
      parent = element.getUserData(CONTAINING_FILE_KEY_AFTER_REPARSE);
    }
    return (FileElement)parent;
  }

  public static FileASTNode getFileElement(@NotNull ASTNode element) {
    ASTNode parent = element;
    while (parent != null && !(parent instanceof FileASTNode)) {
      parent = parent.getTreeParent();
    }
    if (parent == null) {
      parent = element.getUserData(CONTAINING_FILE_KEY_AFTER_REPARSE);
    }
    return (FileASTNode)parent;
  }

  public static @Nullable ASTNode prevLeaf(ASTNode node) {
    return prevLeaf((TreeElement)node, null);
  }

  public static boolean isStrongWhitespaceHolder(IElementType type) {
    return type instanceof IStrongWhitespaceHolderElementType;
  }

  public static String getTokenText(Lexer lexer) {
    return lexer.getBufferSequence().subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
  }

  public static @Nullable LeafElement nextLeaf(@NotNull TreeElement start, CommonParentState commonParent) {
    return (LeafElement)nextLeaf(start, commonParent, null, true);
  }

  public static @Nullable TreeElement nextLeaf(@NotNull TreeElement start,
                                               CommonParentState commonParent,
                                               IElementType searchedType,
                                               boolean expandChameleons) {
    TreeElement element = start;
    while (element != null) {
      if (commonParent != null) {
        commonParent.startLeafBranchStart = element;
        initStrongWhitespaceHolder(commonParent, element, true);
      }
      TreeElement nextTree = element;
      TreeElement next = null;
      while (next == null && (nextTree = nextTree.getTreeNext()) != null) {
        if (nextTree.getElementType() == searchedType) {
          return nextTree;
        }
        next = findFirstLeafOrType(nextTree, searchedType, commonParent, expandChameleons);
      }
      if (next != null) {
        if (commonParent != null) commonParent.nextLeafBranchStart = nextTree;
        return next;
      }
      element = element.getTreeParent();
    }
    return null;
  }

  private static void initStrongWhitespaceHolder(CommonParentState commonParent, ASTNode start, boolean slopeSide) {
    if (start instanceof CompositeElement &&
        (isStrongWhitespaceHolder(start.getElementType()) || slopeSide && start.getUserData(UNCLOSED_ELEMENT_PROPERTY) != null)) {
      commonParent.strongWhiteSpaceHolder = (CompositeElement)start;
      commonParent.isStrongElementOnRisingSlope = slopeSide;
    }
  }

  private static @Nullable TreeElement findFirstLeafOrType(@NotNull TreeElement element,
                                                           IElementType searchedType,
                                                           CommonParentState commonParent,
                                                           boolean expandChameleons) {
    final class MyVisitor extends RecursiveTreeElementWalkingVisitor {
      private TreeElement result;

      private MyVisitor(boolean doTransform) {
        super(doTransform);
      }

      @Override
      protected void visitNode(TreeElement node) {
        if (result != null) return;

        if (commonParent != null) {
          initStrongWhitespaceHolder(commonParent, node, false);
        }
        if (!expandChameleons && isCollapsedChameleon(node) || node instanceof LeafElement || node.getElementType() == searchedType) {
          result = node;
          return;
        }

        super.visitNode(node);
      }
    }

    MyVisitor visitor = new MyVisitor(expandChameleons);
    element.acceptTree(visitor);
    return visitor.result;
  }

  public static @Nullable ASTNode prevLeaf(TreeElement start, @Nullable CommonParentState commonParent) {
    while (true) {
      if (start == null) return null;
      if (commonParent != null) {
        if (commonParent.strongWhiteSpaceHolder != null && start.getUserData(UNCLOSED_ELEMENT_PROPERTY) != null) {
          commonParent.strongWhiteSpaceHolder = (CompositeElement)start;
        }
        commonParent.nextLeafBranchStart = start;
      }
      ASTNode prevTree = start;
      ASTNode prev = null;
      while (prev == null && (prevTree = prevTree.getTreePrev()) != null) {
        prev = findLastLeaf(prevTree);
      }
      if (prev != null) {
        if (commonParent != null) commonParent.startLeafBranchStart = (TreeElement)prevTree;
        return prev;
      }
      start = start.getTreeParent();
    }
  }

  public static @Nullable ASTNode nextLeaf(@Nullable ASTNode start, boolean expandChameleons) {
    while (start != null) {
      for (ASTNode each = start.getTreeNext(); each != null; each = each.getTreeNext()) {
        ASTNode leaf = findFirstLeaf(each, expandChameleons);
        if (leaf != null) return leaf;
      }
      start = start.getTreeParent();
    }
    return null;
  }

  public static @Nullable ASTNode prevLeaf(@Nullable ASTNode start, boolean expandChameleons) {
    while (start != null) {
      for (ASTNode each = start.getTreePrev(); each != null; each = each.getTreePrev()) {
        ASTNode leaf = findLastLeaf(each, expandChameleons);
        if (leaf != null) return leaf;
      }
      start = start.getTreeParent();
    }
    return null;
  }

  public static @Nullable ASTNode getLastChild(ASTNode element) {
    ASTNode child = element;
    while (child != null) {
      element = child;
      child = element.getLastChildNode();
    }
    return element;
  }

  public static boolean containsOuterLanguageElements(@NotNull ASTNode node) {
    AtomicBoolean result = new AtomicBoolean(false);
    ((TreeElement)node).acceptTree(new RecursiveTreeElementWalkingVisitor() {
      @Override
      protected void visitNode(TreeElement element) {
        if (element instanceof OuterLanguageElement) {
          result.set(true);
          stopWalking();
          return;
        }
        super.visitNode(element);
      }
    });
    return result.get();
  }

  public static final class CommonParentState {
    TreeElement startLeafBranchStart;
    public ASTNode nextLeafBranchStart;
    CompositeElement strongWhiteSpaceHolder;
    boolean isStrongElementOnRisingSlope = true;
  }

  public static @Nullable ASTNode skipWhitespaceAndComments(@Nullable ASTNode node, boolean forward) {
    return skipWhitespaceCommentsAndTokens(node, TokenSet.EMPTY, forward);
  }

  public static @Nullable ASTNode skipWhitespaceCommentsAndTokens(@Nullable ASTNode node, @NotNull TokenSet alsoSkip, boolean forward) {
    ASTNode element = node;
    while (true) {
      if (element == null) return null;
      if (!isWhitespaceOrComment(element) && !alsoSkip.contains(element.getElementType())) break;
      element = forward ? element.getTreeNext(): element.getTreePrev();
    }
    return element;
  }

  public static boolean isWhitespaceOrComment(@NotNull ASTNode element) {
    return element.getPsi() instanceof PsiWhiteSpace || element.getPsi() instanceof PsiComment;
  }
}