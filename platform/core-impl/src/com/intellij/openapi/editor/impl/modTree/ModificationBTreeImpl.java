// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.modTree;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@ApiStatus.Internal
public final class ModificationBTreeImpl implements ModificationTree {
  private static final int BRANCHING_FACTOR = 32;
  private static final int MAX_CHILDREN = BRANCHING_FACTOR;
  private static final int MIN_CHILDREN = MAX_CHILDREN / 2;

  private final Node root;
  private final int version0Length;
  private final int currentLength;

  private ModificationBTreeImpl(Node root, int version0Length, int currentLength) {
    if (version0Length < 0) {
      throw new IllegalArgumentException("version0Length must be >= 0: " + version0Length);
    }

    if (currentLength < 0) {
      throw new IllegalArgumentException("currentLength must be >= 0: " + currentLength);
    }

    this.root = root;
    this.version0Length = version0Length;
    this.currentLength = currentLength;
  }

  public static @NotNull ModificationBTreeImpl initial(int length) {
    if (length < 0) {
      throw new IllegalArgumentException("length must be >= 0: " + length);
    }

    Node root = length == 0 ? null : new Leaf(0, length, 0);

    return new ModificationBTreeImpl(root, length, length);
  }

  @Override
  public int toCurrentOffset(int offsetInVersion0) {
    if (offsetInVersion0 < 0 || offsetInVersion0 > version0Length) {
      throw new IndexOutOfBoundsException(
        "offsetInVersion0=" + offsetInVersion0 +
        ", version0Length=" + version0Length
      );
    }

    Node node = root;
    int accumulatedDelta = 0;

    while (node != null) {
      accumulatedDelta += node.delta();

      if (node instanceof Leaf) {
        Leaf leaf = (Leaf)node;

        if (offsetInVersion0 < leaf.start0()) {
          return leaf.start0() + accumulatedDelta;
        }

        if (offsetInVersion0 < leaf.end0()) {
          return offsetInVersion0 + accumulatedDelta;
        }

        return currentLength;
      }

      Branch branch = (Branch)node;
      Node next = null;
      Node[] children = branch.children();

      for (int i = 0; i < children.length; i++) {
        Node child = children[i];

        if (offsetInVersion0 < child.start0()) {
          return accumulatedDelta + child.currentStart();
        }

        if (offsetInVersion0 < child.end0()) {
          next = child;
          break;
        }
      }

      if (next == null) {
        return currentLength;
      }

      node = next;
    }

    return currentLength;
  }

  @Override
  public int toVersion0Offset(int offsetInCurrent) {
    if (offsetInCurrent < 0 || offsetInCurrent > currentLength) {
      throw new IndexOutOfBoundsException(
        "offsetInCurrent=" + offsetInCurrent +
        ", currentLength=" + currentLength
      );
    }

    Node node = root;
    int accumulatedDelta = 0;

    while (node != null) {
      accumulatedDelta += node.delta();

      if (node instanceof Leaf) {
        Leaf leaf = (Leaf)node;

        int currentStart = leaf.start0() + accumulatedDelta;
        int currentEnd = leaf.end0() + accumulatedDelta;

        if (offsetInCurrent < currentStart) {
          return leaf.start0();
        }

        if (offsetInCurrent < currentEnd) {
          return offsetInCurrent - accumulatedDelta;
        }

        return version0Length;
      }

      Branch branch = (Branch)node;
      Node next = null;
      Node[] children = branch.children();

      for (int i = 0; i < children.length; i++) {
        Node child = children[i];

        int childCurrentStart = accumulatedDelta + child.currentStart();
        int childCurrentEnd = accumulatedDelta + child.currentEnd();

        if (offsetInCurrent < childCurrentStart) {
          return child.start0();
        }

        if (offsetInCurrent < childCurrentEnd) {
          next = child;
          break;
        }
      }

      if (next == null) {
        return version0Length;
      }

      node = next;
    }

    return version0Length;
  }

  @Override
  public @NotNull ModificationTree insert(int offsetInCurrent, int length) {
    if (offsetInCurrent < 0 || offsetInCurrent > currentLength) {
      throw new IndexOutOfBoundsException(
        "offsetInCurrent=" + offsetInCurrent +
        ", currentLength=" + currentLength
      );
    }

    if (length < 0) {
      throw new IllegalArgumentException("length must be >= 0: " + length);
    }

    if (length == 0) {
      return this;
    }

    int boundary0 = toVersion0Offset(offsetInCurrent);

    Split split = splitByVersion0(root, boundary0);
    Node shiftedRight = addDelta(split.right(), length);
    Node newRoot = concat(split.left(), shiftedRight);

    return new ModificationBTreeImpl(
      newRoot,
      version0Length,
      currentLength + length
    );
  }

  @Override
  public @NotNull ModificationTree delete(int startInCurrent, int endInCurrent) {
    if (startInCurrent < 0 || startInCurrent > currentLength) {
      throw new IndexOutOfBoundsException(
        "startInCurrent=" + startInCurrent +
        ", currentLength=" + currentLength
      );
    }

    if (endInCurrent < 0 || endInCurrent > currentLength) {
      throw new IndexOutOfBoundsException(
        "endInCurrent=" + endInCurrent +
        ", currentLength=" + currentLength
      );
    }

    if (startInCurrent > endInCurrent) {
      throw new IllegalArgumentException(
        "startInCurrent must be <= endInCurrent: " +
        startInCurrent + " > " + endInCurrent
      );
    }

    int deletedLength = endInCurrent - startInCurrent;

    if (deletedLength == 0) {
      return this;
    }

    int start0 = toVersion0Offset(startInCurrent);
    int end0 = toVersion0Offset(endInCurrent);

    Split first = splitByVersion0(root, start0);
    Split second = splitByVersion0(first.right(), end0);

    Node shiftedRight = addDelta(second.right(), -deletedLength);
    Node newRoot = concat(first.left(), shiftedRight);

    return new ModificationBTreeImpl(
      newRoot,
      version0Length,
      currentLength - deletedLength
    );
  }

  @Override
  public void checkInvariants() {
    if (version0Length < 0) {
      throw new IllegalStateException("version0Length < 0: " + version0Length);
    }

    if (currentLength < 0) {
      throw new IllegalStateException("currentLength < 0: " + currentLength);
    }

    if (root != null) {
      CheckInfo info = checkNode(root, true, 0);

      if (info.start0() < 0) {
        throw new IllegalStateException("root starts before version 0");
      }

      if (info.end0() > version0Length) {
        throw new IllegalStateException(
          "root end exceeds version0Length: " +
          info.end0() + " > " + version0Length
        );
      }

      if (info.currentStart() < 0) {
        throw new IllegalStateException("root currentStart < 0: " + info.currentStart());
      }

      if (info.currentEnd() > currentLength) {
        throw new IllegalStateException(
          "root currentEnd exceeds currentLength: " +
          info.currentEnd() + " > " + currentLength
        );
      }
    }

    int currentEof = toCurrentOffset(version0Length);

    if (currentEof != currentLength) {
      throw new IllegalStateException(
        "version-0 EOF maps to " + currentEof +
        ", but currentLength is " + currentLength
      );
    }

    int version0Eof = toVersion0Offset(currentLength);

    if (version0Eof != version0Length) {
      throw new IllegalStateException(
        "current EOF maps to " + version0Eof +
        ", but version0Length is " + version0Length
      );
    }
  }

  /// Node model.
  ///
  /// The tree stores only surviving original text.
  ///
  /// `Leaf(start0, end0, delta)` represents one live version-0 range:
  ///
  /// `[start0, end0)`
  ///
  /// Its current-version range is:
  ///
  /// `[start0 + effectiveDelta, end0 + effectiveDelta)`
  ///
  /// where:
  ///
  /// `effectiveDelta = sum(delta on path from root to leaf)`
  ///
  /// Deleted original text is absent from the tree.
  /// Inserted current text is represented as gaps between transformed leaves.
  private interface Node {
    int delta();

    int start0();

    int end0();

    int currentStart();

    int currentEnd();

    int height();

    int runCount();
  }

  private static final class Leaf implements Node {
    private final int start0;
    private final int end0;
    private final int delta;

    private Leaf(int start0, int end0, int delta) {
      if (start0 >= end0) {
        throw new IllegalArgumentException(
          "Invalid leaf range: [" + start0 + ", " + end0 + ")"
        );
      }

      this.start0 = start0;
      this.end0 = end0;
      this.delta = delta;
    }

    @Override
    public int delta() {
      return delta;
    }

    @Override
    public int start0() {
      return start0;
    }

    @Override
    public int end0() {
      return end0;
    }

    @Override
    public int currentStart() {
      return start0 + delta;
    }

    @Override
    public int currentEnd() {
      return end0 + delta;
    }

    @Override
    public int height() {
      return 0;
    }

    @Override
    public int runCount() {
      return 1;
    }

    @NotNull
    private Leaf addDelta(int deltaDiff) {
      if (deltaDiff == 0) {
        return this;
      }

      return new Leaf(start0, end0, delta + deltaDiff);
    }
  }

  private static final class Branch implements Node {
    private final int delta;
    private final Node[] children;
    private final int start0;
    private final int end0;
    private final int currentStart;
    private final int currentEnd;
    private final int height;
    private final int runCount;

    @NotNull
    private static Branch of(int delta, @NotNull Node[] children) {
      Objects.requireNonNull(children, "children");

      if (children.length < 2) {
        throw new IllegalArgumentException("Branch must contain at least two children");
      }

      if (children.length > MAX_CHILDREN) {
        throw new IllegalArgumentException("Too many children: " + children.length);
      }

      int childHeight = children[0].height();
      int runCount = 0;

      for (int i = 0; i < children.length; i++) {
        Node child = children[i];

        Objects.requireNonNull(child, "children[" + i + "]");

        if (child.height() != childHeight) {
          throw new IllegalArgumentException("Children must have equal height");
        }

        if (i > 0 && children[i - 1].end0() > child.start0()) {
          throw new IllegalArgumentException("Overlapping version-0 child ranges");
        }

        if (i > 0 && children[i - 1].currentEnd() > child.currentStart()) {
          throw new IllegalArgumentException("Overlapping current child ranges");
        }

        runCount += child.runCount();
      }

      Node first = children[0];
      Node last = children[children.length - 1];

      return new Branch(
        delta,
        children,
        first.start0(),
        last.end0(),
        delta + first.currentStart(),
        delta + last.currentEnd(),
        childHeight + 1,
        runCount
      );
    }

    private Branch(
      int delta,
      @NotNull Node[] children,
      int start0,
      int end0,
      int currentStart,
      int currentEnd,
      int height,
      int runCount
    ) {
      this.delta = delta;
      this.children = children;
      this.start0 = start0;
      this.end0 = end0;
      this.currentStart = currentStart;
      this.currentEnd = currentEnd;
      this.height = height;
      this.runCount = runCount;
    }

    @Override
    public int delta() {
      return delta;
    }

    @Override
    public int start0() {
      return start0;
    }

    @Override
    public int end0() {
      return end0;
    }

    @Override
    public int currentStart() {
      return currentStart;
    }

    @Override
    public int currentEnd() {
      return currentEnd;
    }

    @Override
    public int height() {
      return height;
    }

    @Override
    public int runCount() {
      return runCount;
    }

    @NotNull
    private Node[] children() {
      return children;
    }

    @NotNull
    private Branch addDelta(int deltaDiff) {
      if (deltaDiff == 0) {
        return this;
      }

      return Branch.of(delta + deltaDiff, children);
    }
  }

  private static final class Split {
    private final Node left;
    private final Node right;

    private Split(Node left, Node right) {
      this.left = left;
      this.right = right;
    }

    private Node left() {
      return left;
    }

    private Node right() {
      return right;
    }
  }

  private static final class CheckInfo {
    private final int start0;
    private final int end0;
    private final int currentStart;
    private final int currentEnd;
    private final int height;
    private final int runCount;
    private final int firstEffectiveDelta;
    private final int lastEffectiveDelta;

    private CheckInfo(
      int start0,
      int end0,
      int currentStart,
      int currentEnd,
      int height,
      int runCount,
      int firstEffectiveDelta,
      int lastEffectiveDelta
    ) {
      this.start0 = start0;
      this.end0 = end0;
      this.currentStart = currentStart;
      this.currentEnd = currentEnd;
      this.height = height;
      this.runCount = runCount;
      this.firstEffectiveDelta = firstEffectiveDelta;
      this.lastEffectiveDelta = lastEffectiveDelta;
    }

    private int start0() {
      return start0;
    }

    private int end0() {
      return end0;
    }

    private int currentStart() {
      return currentStart;
    }

    private int currentEnd() {
      return currentEnd;
    }

    private int height() {
      return height;
    }

    private int runCount() {
      return runCount;
    }

    private int firstEffectiveDelta() {
      return firstEffectiveDelta;
    }

    private int lastEffectiveDelta() {
      return lastEffectiveDelta;
    }
  }

  /// Split the tree by version-0 coordinate.
  ///
  /// Result:
  ///
  /// - `left`: all live original ranges before `boundary0`
  /// - `right`: all live original ranges at or after `boundary0`
  ///
  /// If `boundary0` falls inside a leaf, the leaf is split into two leaves.
  @NotNull
  private static Split splitByVersion0(Node node, int boundary0) {
    if (node == null) {
      return new Split(null, null);
    }

    if (boundary0 <= node.start0()) {
      return new Split(null, node);
    }

    if (boundary0 >= node.end0()) {
      return new Split(node, null);
    }

    if (node instanceof Leaf) {
      Leaf leaf = (Leaf)node;

      Leaf left = new Leaf(leaf.start0(), boundary0, leaf.delta());
      Leaf right = new Leaf(boundary0, leaf.end0(), leaf.delta());

      return new Split(left, right);
    }

    Branch branch = (Branch)node;
    Node[] children = materializedChildren(branch);

    ArrayList<Node> left = new ArrayList<Node>(children.length);
    ArrayList<Node> right = new ArrayList<Node>(children.length);

    boolean splitDone = false;

    for (int i = 0; i < children.length; i++) {
      Node child = children[i];

      if (splitDone) {
        appendNode(right, child);
        continue;
      }

      if (boundary0 <= child.start0()) {
        appendNode(right, child);
        splitDone = true;
      }
      else if (boundary0 >= child.end0()) {
        appendNode(left, child);
      }
      else {
        Split childSplit = splitByVersion0(child, boundary0);

        if (childSplit.left() != null) {
          appendNode(left, childSplit.left());
        }

        if (childSplit.right() != null) {
          appendNode(right, childSplit.right());
        }

        splitDone = true;
      }
    }

    return new Split(
      concatAll(left),
      concatAll(right)
    );
  }

  /// Concatenate two ordered trees with fringe repacking.
  ///
  /// This descends only through the touching fringes:
  ///
  /// - right fringe of `left`
  /// - left fringe of `right`
  ///
  /// Boundary nodes are repacked evenly, which prevents the tree from
  /// degrading into a binary-shaped tree after many edits.
  private static Node concat(Node left, Node right) {
    if (left == null) {
      return right;
    }

    if (right == null) {
      return left;
    }

    return packUpAsRoot(appendNodes(left, right));
  }

  private static Node concatAll(@NotNull List<Node> nodes) {
    Node result = null;

    for (int i = 0; i < nodes.size(); i++) {
      result = concat(result, nodes.get(i));
    }

    return result;
  }

  @NotNull
  private static List<Node> appendNodes(@NotNull Node left, @NotNull Node right) {
    if (left.height() == right.height()) {
      if (left instanceof Leaf && right instanceof Leaf) {
        ArrayList<Node> nodes = new ArrayList<Node>(2);

        appendNode(nodes, left);
        appendNode(nodes, right);

        return nodes;
      }

      Branch leftBranch = (Branch)left;
      Branch rightBranch = (Branch)right;

      Node[] leftChildren = materializedChildren(leftBranch);
      Node[] rightChildren = materializedChildren(rightBranch);

      ArrayList<Node> children = new ArrayList<Node>(leftChildren.length + rightChildren.length);

      for (int i = 0; i < leftChildren.length - 1; i++) {
        appendNode(children, leftChildren[i]);
      }

      List<Node> boundary = appendNodes(leftChildren[leftChildren.length - 1], rightChildren[0]);

      for (int i = 0; i < boundary.size(); i++) {
        appendNode(children, boundary.get(i));
      }

      for (int i = 1; i < rightChildren.length; i++) {
        appendNode(children, rightChildren[i]);
      }

      return packChildren(children);
    }

    if (left.height() > right.height()) {
      Branch leftBranch = (Branch)left;
      Node[] children = materializedChildren(leftBranch);

      ArrayList<Node> newChildren = new ArrayList<Node>(children.length + 1);

      for (int i = 0; i < children.length - 1; i++) {
        appendNode(newChildren, children[i]);
      }

      List<Node> boundary = appendNodes(children[children.length - 1], right);

      for (int i = 0; i < boundary.size(); i++) {
        appendNode(newChildren, boundary.get(i));
      }

      return packChildren(newChildren);
    }

    Branch rightBranch = (Branch)right;
    Node[] children = materializedChildren(rightBranch);

    ArrayList<Node> newChildren = new ArrayList<Node>(children.length + 1);

    List<Node> boundary = appendNodes(left, children[0]);

    for (int i = 0; i < boundary.size(); i++) {
      appendNode(newChildren, boundary.get(i));
    }

    for (int i = 1; i < children.length; i++) {
      appendNode(newChildren, children[i]);
    }

    return packChildren(newChildren);
  }

  /// Pack same-height nodes into branch nodes.
  ///
  /// Root nodes may be underfull.
  /// Non-root branches are expected to have at least `MIN_CHILDREN` children.
  private static Node packUpAsRoot(@NotNull List<Node> nodes) {
    if (nodes.isEmpty()) {
      return null;
    }

    List<Node> level = nodes;

    while (level.size() > 1) {
      level = packChildren(level);
    }

    return level.get(0);
  }

  @NotNull
  private static List<Node> packChildren(@NotNull List<Node> children) {
    if (children.isEmpty()) {
      return Collections.emptyList();
    }

    if (children.size() == 1) {
      return Collections.singletonList(children.get(0));
    }

    int height = children.get(0).height();

    for (int i = 0; i < children.size(); i++) {
      Node child = children.get(i);

      if (child.height() != height) {
        throw new IllegalArgumentException(
          "Cannot pack children with different heights"
        );
      }
    }

    int groupCount = groupCount(children.size(), MAX_CHILDREN);
    ArrayList<Node> result = new ArrayList<Node>(groupCount);

    int baseSize = children.size() / groupCount;
    int remainder = children.size() % groupCount;
    int index = 0;

    for (int group = 0; group < groupCount; group++) {
      int groupSize = baseSize + (group < remainder ? 1 : 0);

      Node[] groupChildren = new Node[groupSize];

      for (int i = 0; i < groupSize; i++) {
        groupChildren[i] = children.get(index++);
      }

      if (groupChildren.length == 1) {
        result.add(groupChildren[0]);
      }
      else {
        result.add(Branch.of(0, groupChildren));
      }
    }

    return result;
  }

  private static int groupCount(int itemCount, int maxGroupSize) {
    if (itemCount <= 0) {
      throw new IllegalArgumentException("itemCount must be positive: " + itemCount);
    }

    return (itemCount + maxGroupSize - 1) / maxGroupSize;
  }

  /// Coalesce adjacent leaves with equal effective delta.
  ///
  /// This keeps repeated insert/delete around the same original boundary from
  /// producing unnecessary fragmentation.
  private static void appendNode(@NotNull List<Node> nodes, @NotNull Node node) {
    if (nodes.isEmpty()) {
      nodes.add(node);
      return;
    }

    Node last = nodes.get(nodes.size() - 1);

    if (last instanceof Leaf && node instanceof Leaf) {
      Leaf leftLeaf = (Leaf)last;
      Leaf rightLeaf = (Leaf)node;

      if (canMerge(leftLeaf, rightLeaf)) {
        nodes.set(
          nodes.size() - 1,
          new Leaf(leftLeaf.start0(), rightLeaf.end0(), leftLeaf.delta())
        );
        return;
      }
    }

    nodes.add(node);
  }

  private static boolean canMerge(@NotNull Leaf left, @NotNull Leaf right) {
    return left.end0() == right.start0()
           && left.delta() == right.delta();
  }

  /// Add delta to an entire subtree.
  ///
  /// Since deltas are path-summed, this only changes the subtree root.
  private static Node addDelta(Node node, int deltaDiff) {
    if (node == null || deltaDiff == 0) {
      return node;
    }

    if (node instanceof Leaf) {
      return ((Leaf)node).addDelta(deltaDiff);
    }

    return ((Branch)node).addDelta(deltaDiff);
  }

  @NotNull
  private static Node[] materializedChildren(@NotNull Branch branch) {
    Node[] source = branch.children();
    Node[] result = new Node[source.length];

    for (int i = 0; i < source.length; i++) {
      result[i] = addDelta(source[i], branch.delta());
    }

    return result;
  }

  /// Invariant checking.
  @NotNull
  private CheckInfo checkNode(
    @NotNull Node node,
    boolean isRoot,
    int accumulatedDeltaBeforeNode
  ) {
    if (node instanceof Leaf) {
      return checkLeaf((Leaf)node, accumulatedDeltaBeforeNode);
    }

    return checkBranch((Branch)node, isRoot, accumulatedDeltaBeforeNode);
  }

  @NotNull
  private CheckInfo checkLeaf(
    @NotNull Leaf leaf,
    int accumulatedDeltaBeforeLeaf
  ) {
    if (leaf.start0() < 0) {
      throw new IllegalStateException("leaf starts before version 0");
    }

    if (leaf.end0() > version0Length) {
      throw new IllegalStateException(
        "leaf end exceeds version0Length: " +
        leaf.end0() + " > " + version0Length
      );
    }

    if (leaf.start0() >= leaf.end0()) {
      throw new IllegalStateException(
        "invalid leaf range: [" + leaf.start0() + ", " + leaf.end0() + ")"
      );
    }

    if (leaf.height() != 0) {
      throw new IllegalStateException("leaf height must be 0");
    }

    if (leaf.runCount() != 1) {
      throw new IllegalStateException("leaf runCount must be 1");
    }

    int effectiveDelta = accumulatedDeltaBeforeLeaf + leaf.delta();
    int currentStart = leaf.start0() + effectiveDelta;
    int currentEnd = leaf.end0() + effectiveDelta;

    if (currentStart < 0) {
      throw new IllegalStateException("leaf currentStart < 0: " + currentStart);
    }

    if (currentEnd > currentLength) {
      throw new IllegalStateException(
        "leaf currentEnd exceeds currentLength: " +
        currentEnd + " > " + currentLength
      );
    }

    if (currentStart >= currentEnd) {
      throw new IllegalStateException(
        "invalid leaf current range: [" + currentStart + ", " + currentEnd + ")"
      );
    }

    if (accumulatedDeltaBeforeLeaf + leaf.currentStart() != currentStart) {
      throw new IllegalStateException("leaf currentStart summary mismatch");
    }

    if (accumulatedDeltaBeforeLeaf + leaf.currentEnd() != currentEnd) {
      throw new IllegalStateException("leaf currentEnd summary mismatch");
    }

    return new CheckInfo(
      leaf.start0(),
      leaf.end0(),
      currentStart,
      currentEnd,
      0,
      1,
      effectiveDelta,
      effectiveDelta
    );
  }

  @NotNull
  private CheckInfo checkBranch(
    @NotNull Branch branch,
    boolean isRoot,
    int accumulatedDeltaBeforeBranch
  ) {
    Node[] children = branch.children();

    if (children.length == 0) {
      throw new IllegalStateException("empty branch");
    }

    if (children.length == 1) {
      throw new IllegalStateException("branch with one child should be collapsed");
    }

    if (children.length > MAX_CHILDREN) {
      throw new IllegalStateException(
        "branch has too many children: " + children.length
      );
    }

    if (!isRoot && children.length < MIN_CHILDREN) {
      throw new IllegalStateException(
        "non-root branch is underfull: " +
        children.length + " < " + MIN_CHILDREN
      );
    }

    int accumulatedDelta = accumulatedDeltaBeforeBranch + branch.delta();
    int childHeight = children[0].height();
    int expectedRunCount = 0;

    CheckInfo firstInfo = null;
    CheckInfo previousInfo = null;
    CheckInfo lastInfo = null;

    for (int i = 0; i < children.length; i++) {
      Node child = children[i];

      if (child == null) {
        throw new IllegalStateException("null child");
      }

      if (child.height() != childHeight) {
        throw new IllegalStateException("children with different heights");
      }

      CheckInfo childInfo = checkNode(child, false, accumulatedDelta);

      expectedRunCount += childInfo.runCount();

      if (firstInfo == null) {
        firstInfo = childInfo;
      }

      if (previousInfo != null) {
        if (previousInfo.end0() > childInfo.start0()) {
          throw new IllegalStateException("overlapping version-0 child ranges");
        }

        if (previousInfo.currentEnd() > childInfo.currentStart()) {
          throw new IllegalStateException("overlapping current child ranges");
        }

        if (
          previousInfo.end0() == childInfo.start0() &&
          previousInfo.lastEffectiveDelta() == childInfo.firstEffectiveDelta()
        ) {
          throw new IllegalStateException(
            "mergeable adjacent leaves remain across child boundary"
          );
        }
      }

      previousInfo = childInfo;
      lastInfo = childInfo;
    }

    if (firstInfo == null || lastInfo == null) {
      throw new IllegalStateException("unreachable empty branch");
    }

    int expectedHeight = childHeight + 1;

    if (branch.height() != expectedHeight) {
      throw new IllegalStateException(
        "branch height mismatch: " +
        branch.height() + " != " + expectedHeight
      );
    }

    if (branch.runCount() != expectedRunCount) {
      throw new IllegalStateException(
        "branch runCount mismatch: " +
        branch.runCount() + " != " + expectedRunCount
      );
    }

    if (branch.start0() != firstInfo.start0()) {
      throw new IllegalStateException("branch start0 summary mismatch");
    }

    if (branch.end0() != lastInfo.end0()) {
      throw new IllegalStateException("branch end0 summary mismatch");
    }

    if (accumulatedDeltaBeforeBranch + branch.currentStart() != firstInfo.currentStart()) {
      throw new IllegalStateException("branch currentStart summary mismatch");
    }

    if (accumulatedDeltaBeforeBranch + branch.currentEnd() != lastInfo.currentEnd()) {
      throw new IllegalStateException("branch currentEnd summary mismatch");
    }

    return new CheckInfo(
      firstInfo.start0(),
      lastInfo.end0(),
      firstInfo.currentStart(),
      lastInfo.currentEnd(),
      expectedHeight,
      expectedRunCount,
      firstInfo.firstEffectiveDelta(),
      lastInfo.lastEffectiveDelta()
    );
  }
}