/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.impl;

import com.intellij.util.BitUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * User: cdr
 */
public abstract class RedBlackTree<K> {
  public static boolean VERIFY;
  private static final int INDENT_STEP = 4;
  private int nodeSize; // number of nodes
  protected int modCount;
  protected Node<K> root;

  public RedBlackTree() {
    root = null;
    verifyProperties();
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

  protected void replaceNode(@NotNull Node<K> oldn, Node<K> newn) {
    Node<K> parent = oldn.getParent();
    if (parent == null) {
      root = newn;
    }
    else {
      if (oldn == parent.getLeft()) {
        parent.setLeft(newn);
      }
      else {
        parent.setRight(newn);
      }
    }
    if (newn != null) {
      newn.setParent(parent);
    }
    //oldn.parent = null;
    //oldn.left = null;
    //oldn.right = null;
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
    modCount++;

    Node<K> e = n;
    while (e.getParent() != null) e = e.getParent();
    assert e == root : e; // assert the node belongs to our tree

    if (n.getLeft() != null && n.getRight() != null) {
      // Copy key/value from predecessor and then delete it instead
      Node<K> pred = maximumNode(n.getLeft());

      //Color c = pred.color;
      //swap(n, pred);
      //assert pred.color == c;
      //pred.color = n.color;
      //n.color = c;

      n = swapWithMaxPred(n, pred);
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

  @NotNull
  protected abstract Node<K> swapWithMaxPred(@NotNull Node<K> nowAscendant, @NotNull Node<K> nowDescendant);

  @NotNull
  protected Node<K> maximumNode(@NotNull Node<K> n) {
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

  public void print() {
    printHelper(root, 0);
  }

  private static void printHelper(Node<?> n, int indent) {
    if (n == null) {
      System.err.print("<empty tree>");
      return;
    }
    if (n.getRight() != null) {
      printHelper(n.getRight(), indent + INDENT_STEP);
    }
    for (int i = 0; i < indent; i++) {
      System.err.print(" ");
    }
    if (n.isBlack()) {
      System.err.println(n);
    }
    else {
      System.err.println("<" + n + ">");
    }
    if (n.getLeft() != null) {
      printHelper(n.getLeft(), indent + INDENT_STEP);
    }
  }

  public abstract static class Node<K> {
    protected Node<K> left;
    protected Node<K> right;
    protected Node<K> parent;

    private volatile byte myFlags;
    static final byte COLOR_MASK = 1;

    boolean isFlagSet(byte mask) {
      return BitUtil.isSet(myFlags, mask);
    }

    protected void setFlag(byte mask, boolean value) {
      myFlags = BitUtil.set(myFlags, mask, value);
    }


    public Node<K> grandparent() {
      assert getParent() != null; // Not the root node
      assert getParent().getParent() != null; // Not child of root
      return getParent().getParent();
    }

    public Node<K> sibling() {
      Node<K> parent = getParent();
      assert parent != null; // Root node has no sibling
      return this == parent.getLeft() ? parent.getRight() : parent.getLeft();
    }

    private Node<K> uncle() {
      assert getParent() != null; // Root node has no uncle
      assert getParent().getParent() != null; // Children of root have no uncle
      return getParent().sibling();
    }

    public Node<K> getLeft() {
      return left;
    }

    public void setLeft(Node<K> left) {
      this.left = left;
    }

    public Node<K> getRight() {
      return right;
    }

    public void setRight(Node<K> right) {
      this.right = right;
    }

    public Node<K> getParent() {
      return parent;
    }

    public void setParent(Node<K> parent) {
      this.parent = parent;
    }

    public abstract boolean processAliveKeys(@NotNull Processor<? super K> processor);

    public abstract boolean hasAliveKey(boolean purgeDead);

    public boolean isBlack() {
      return isFlagSet(COLOR_MASK);
    }
    private void setBlack() {
      setFlag(COLOR_MASK, true);
    }
    void setRed() {
      setFlag(COLOR_MASK, false);
    }
    public void setColor(boolean isBlack) {
      setFlag(COLOR_MASK, isBlack);
    }
  }

  public int size() {
    return nodeSize;
  }
  int nodeSize() {
    return nodeSize;
  }

  void verifyProperties() {
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
    assert !isBlack(n) || isBlack(n);
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

  public void clear() {
    modCount++;
    root = null;
    nodeSize = 0;
  }
}
