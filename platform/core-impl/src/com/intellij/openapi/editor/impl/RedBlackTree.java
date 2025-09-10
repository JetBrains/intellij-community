// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.util.BitUtil;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.VarHandleWrapper;
import org.jetbrains.annotations.*;


@ApiStatus.Internal
public abstract class RedBlackTree<K> {
  static boolean VERIFY;
  private int nodeSize; // number of nodes
  protected Node<K> root;
  @SuppressWarnings("unused") private volatile int modCount;
  private static final VarHandleWrapper MOD_COUNT_HANDLE = VarHandleWrapper.getFactory().create(RedBlackTree.class, "modCount", int.class);

  RedBlackTree() {
    root = null;
    verifyProperties();
  }

  void incModCount() {
    MOD_COUNT_HANDLE.getAndAdd(this, 1);
  }
  int getModCount() {
    return modCount;
  }

  protected void rotateLeft(@NotNull Node<K> n) {
    Node<K> r = n.getRight();
    replaceNode(n, r);
    n.setRight(r.getLeft());
    if (r.getLeft() != null) {
      r.getLeft().setParent(n);
    }
    r.setLeft(n);
    n.setParent(r);
  }

  protected void rotateRight(@NotNull Node<K> n) {
    Node<K> l = n.getLeft();
    replaceNode(n, l);
    n.setLeft(l.getRight());
    if (l.getRight() != null) {
      l.getRight().setParent(n);
    }
    l.setRight(n);
    n.setParent(l);
  }

  protected void replaceNode(@NotNull Node<K> oldN, Node<K> newN) {
    Node<K> parent = oldN.getParent();
    if (parent == null) {
      root = newN;
    }
    else {
      if (oldN == parent.getLeft()) {
        parent.setLeft(newN);
      }
      else {
        parent.setRight(newN);
      }
    }
    if (newN != null) {
      newN.setParent(parent);
    }
  }

  void onInsertNode() {
    nodeSize++;
  }

  void insertCase1(Node<K> n) {
    if (n.getParent() == null) {
      n.setBlack();
    }
    else {
      insertCase2(n);
    }
  }

  private void insertCase2(Node<K> n) {
    if (!isBlack(n.getParent())) {
      insertCase3(n);
    }
    // Tree is still valid
  }

  private void insertCase3(Node<K> n) {
    if (!isBlack(n.uncle())) {
      n.getParent().setBlack();
      n.uncle().setBlack();
      n.grandparent().setRed();
      insertCase1(n.grandparent());
    }
    else {
      insertCase4(n);
    }
  }

  private void insertCase4(Node<K> n) {
    if (n == n.getParent().getRight() && n.getParent() == n.grandparent().getLeft()) {
      rotateLeft(n.getParent());
      n = n.getLeft();
    }
    else if (n == n.getParent().getLeft() && n.getParent() == n.grandparent().getRight()) {
      rotateRight(n.getParent());
      n = n.getRight();
    }
    insertCase5(n);
  }

  private void insertCase5(Node<K> n) {
    n.getParent().setBlack();
    n.grandparent().setRed();
    if (n == n.getParent().getLeft() && n.getParent() == n.grandparent().getLeft()) {
      rotateRight(n.grandparent());
    }
    else {
      assert n == n.getParent().getRight();
      assert n.getParent() == n.grandparent().getRight();
      rotateLeft(n.grandparent());
    }
  }

  private static <K> void assertParentChild(Node<K> node1) {
    assert node1 == null || node1.getParent() == null || node1.getParent().getLeft() == node1 || node1.getParent().getRight() == node1;
  }

  protected void deleteNode(@NotNull Node<K> n) {
    incModCount();

    Node<K> e = n;
    while (e.getParent() != null) e = e.getParent();
    assert e == root : e; // assert the node belongs to our tree

    if (n.getLeft() != null && n.getRight() != null) {
      // Copy key/value from predecessor and then delete it instead
      Node<K> predNode = maximumNode(n.getLeft());
      n = swapWithMaxPred(n, predNode);
    }

    assert n.getLeft() == null || n.getRight() == null;
    Node<K> child = n.getRight() == null ? n.getLeft() : n.getRight();
    if (isBlack(n)) {
      n.setColor(isBlack(child));
      deleteCase1(n);
    }
    replaceNode(n, child);

    if (!isBlack(root)) {
      root.setBlack();
    }

    assert nodeSize > 0 : nodeSize;
    nodeSize--;
    verifyProperties();
  }

  protected abstract @NotNull Node<K> swapWithMaxPred(@NotNull Node<K> nowAscendant, @NotNull Node<K> nowDescendant);

  protected @NotNull Node<K> maximumNode(@NotNull Node<K> n) {
    while (n.getRight() != null) {
      n = n.getRight();
    }
    return n;
  }

  private void deleteCase1(Node<K> n) {
    if (n.getParent() != null) {
      deleteCase2(n);
    }
  }

  private void deleteCase2(Node<K> n) {
    if (!isBlack(n.sibling())) {
      n.getParent().setRed();
      n.sibling().setBlack();
      if (n == n.getParent().getLeft()) {
        rotateLeft(n.getParent());
      }
      else {
        rotateRight(n.getParent());
      }
    }
    deleteCase3(n);
  }

  private void deleteCase3(Node<K> n) {
    if (isBlack(n.getParent()) &&
        isBlack(n.sibling()) &&
        isBlack(n.sibling().getLeft()) &&
        isBlack(n.sibling().getRight())) {
      n.sibling().setRed();
      deleteCase1(n.getParent());
    }
    else {
      deleteCase4(n);
    }
  }

  private void deleteCase4(Node<K> n) {
    if (!isBlack(n.getParent()) &&
        isBlack(n.sibling()) &&
        isBlack(n.sibling().getLeft()) &&
        isBlack(n.sibling().getRight())) {
      n.sibling().setRed();
      n.getParent().setBlack();
    }
    else {
      deleteCase5(n);
    }
  }

  private void deleteCase5(Node<K> n) {
    if (n == n.getParent().getLeft() &&
        isBlack(n.sibling()) &&
        !isBlack(n.sibling().getLeft()) &&
        isBlack(n.sibling().getRight())) {
      n.sibling().setRed();
      n.sibling().getLeft().setBlack();
      rotateRight(n.sibling());
    }
    else if (n == n.getParent().getRight() &&
             isBlack(n.sibling()) &&
             !isBlack(n.sibling().getRight()) &&
             isBlack(n.sibling().getLeft())) {
      n.sibling().setRed();
      n.sibling().getRight().setBlack();
      rotateLeft(n.sibling());
    }
    deleteCase6(n);
  }

  private void deleteCase6(Node<K> n) {
    n.sibling().setColor(isBlack(n.getParent()));
    n.getParent().setBlack();
    if (n == n.getParent().getLeft()) {
      assert !isBlack(n.sibling().getRight());
      n.sibling().getRight().setBlack();
      rotateLeft(n.getParent());
    }
    else {
      assert !isBlack(n.sibling().getLeft());
      n.sibling().getLeft().setBlack();
      rotateRight(n.getParent());
    }
  }

  @TestOnly
  @ApiStatus.Internal
  public static void runAssertingInternalInvariants(@NotNull ThrowableRunnable<?> runnable) throws Throwable {
    boolean old = VERIFY;
    VERIFY = true;
    try {
      runnable.run();
    }
    finally {
      VERIFY = old;
    }
  }

  protected abstract static class Node<K> {
    protected Node<K> left;
    protected Node<K> right;
    protected Node<K> parent;

    @SuppressWarnings("unused")
    private volatile byte myFlags;
    private static final VarHandleWrapper MY_FLAGS_HANDLER = VarHandleWrapper.getFactory().create(Node.class, "myFlags", byte.class);
    static final byte COLOR_MASK = 1;

    @Contract(pure = true)
    @ApiStatus.Internal
    protected boolean isFlagSet(byte mask) {
      return BitUtil.isSet(myFlags, mask);
    }

    protected void setFlag(byte mask, boolean value) {
      byte flags;
      do {
        flags = myFlags;
      }
      while (!MY_FLAGS_HANDLER.compareAndSetByte(this, flags, BitUtil.set(flags, mask, value)));
    }

    private Node<K> grandparent() {
      assert getParent() != null; // Not the root node
      assert getParent().getParent() != null; // Not child of root
      return getParent().getParent();
    }

    private Node<K> sibling() {
      Node<K> parent = getParent();
      assert parent != null; // Root node has no sibling
      return this == parent.getLeft() ? parent.getRight() : parent.getLeft();
    }

    private Node<K> uncle() {
      assert getParent() != null; // Root node has no uncle
      assert getParent().getParent() != null; // Children of root have no uncle
      return getParent().sibling();
    }

    protected Node<K> getLeft() {
      return left;
    }

    protected void setLeft(Node<K> left) {
      this.left = left;
    }

    protected Node<K> getRight() {
      return right;
    }

    protected void setRight(Node<K> right) {
      this.right = right;
    }

    protected Node<K> getParent() {
      return parent;
    }

    protected void setParent(Node<K> parent) {
      this.parent = parent;
    }

    protected abstract boolean processAliveKeys(@NotNull Processor<? super K> processor);

    protected abstract boolean hasAliveKey(boolean purgeDead);

    @Contract(pure = true)
    protected boolean isBlack() {
      return isFlagSet(COLOR_MASK);
    }
    private void setBlack() {
      setFlag(COLOR_MASK, true);
    }
    void setRed() {
      setFlag(COLOR_MASK, false);
    }
    protected void setColor(boolean isBlack) {
      setFlag(COLOR_MASK, isBlack);
    }
  }

  protected int size() {
    return nodeSize;
  }
  int nodeSize() {
    return nodeSize;
  }

  protected void verifyProperties() {
    //if (true) return;
    if (VERIFY) {
      verifyProperty1(root);
      verifyProperty2(root);
      // Property 3 is implicit
      verifyProperty4(root);
      verifyProperty5(root);
    }
  }

  private static void verifyProperty1(Node<?> n) {
    if (n == null) return;
    assert n.getParent() != n;
    assert n.getLeft() != n;
    assert n.getRight() != n;
    assertParentChild(n);

    verifyProperty1(n.getLeft());
    verifyProperty1(n.getRight());
  }

  private static void verifyProperty2(Node<?> root) {
    assert isBlack(root);
  }

  @Contract(pure = true)
  private static boolean isBlack(@Nullable Node<?> n) {
    return n == null || n.isBlack();
  }

  private static void verifyProperty4(Node<?> n) {
    if (!isBlack(n)) {
      assert isBlack(n.getLeft());
      assert isBlack(n.getRight());
      assert isBlack(n.getParent());
    }
    if (n == null) return;
    verifyProperty4(n.getLeft());
    verifyProperty4(n.getRight());
  }

  private static void verifyProperty5(Node<?> root) {
    verifyProperty5Helper(root, 0, -1);
  }

  private static int verifyProperty5Helper(Node<?> n, int blackCount, int pathBlackCount) {
    if (isBlack(n)) {
      blackCount++;
    }
    if (n == null) {
      if (pathBlackCount == -1) {
        pathBlackCount = blackCount;
      }
      else {
        assert blackCount == pathBlackCount;
      }
      return pathBlackCount;
    }
    pathBlackCount = verifyProperty5Helper(n.getLeft(), blackCount, pathBlackCount);
    pathBlackCount = verifyProperty5Helper(n.getRight(), blackCount, pathBlackCount);

    return pathBlackCount;
  }

  protected void clear() {
    incModCount();

    root = null;
    nodeSize = 0;
  }
}
