// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.modTree;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ModificationTreeImpl implements ModificationTree {
  private final Node root;
  private final int version0Length;
  private final int currentLength;

  private ModificationTreeImpl(Node root, int version0Length, int currentLength) {
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

  public static @NotNull ModificationTreeImpl initial(int length) {
    if (length < 0) {
      throw new IllegalArgumentException("length must be >= 0: " + length);
    }

    Node root = length == 0
                ? null
                : new Node(0, length, 0, null, null);

    return new ModificationTreeImpl(root, length, length);
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

    // If offsetInCurrent is inside inserted text, it is not contained by any
    // live original run. In that case we return the version-0 boundary after
    // the inserted gap, i.e. right-biased mapping.
    //
    // Example:
    //
    //   version0:  "hello world"
    //   current:   "hello beautiful world"
    //
    // Live runs:
    //   [0,6)  delta 0       -> current [0,6)
    //   [6,11) delta +10     -> current [16,21)
    //
    // Current offsets [6,16) are inserted text.
    // toVersion0Offset(10) returns 6.
    int successorVersion0Start = version0Length;
    boolean hasSuccessor = false;

    while (node != null) {
      accumulatedDelta += node.delta;

      int currentStart = node.start0 + accumulatedDelta;
      int currentEnd = node.end0 + accumulatedDelta;

      if (offsetInCurrent < currentStart) {
        // Target is before this node's current range.
        // This node is the best "next original run" candidate so far.
        successorVersion0Start = node.start0;
        hasSuccessor = true;
        node = node.left;
      }
      else if (offsetInCurrent >= currentEnd) {
        // Target is after this node's current range.
        node = node.right;
      }
      else {
        // Target lies inside surviving original text.
        return offsetInCurrent - accumulatedDelta;
      }
    }

    // Not found => offset is inside inserted current text, or EOF.
    //
    // Right-biased result:
    //   - inside inserted gap: return next live run's version-0 start
    //   - after last live run / EOF: return version0Length
    return hasSuccessor ? successorVersion0Start : version0Length;
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

    // If offsetInVersion0 is inside deleted original text, it will not be
    // contained by any live node. In that case we return the current start of
    // the next live node, i.e. right-biased mapping.
    //
    // Example:
    //
    //   version0:  abcdef
    //   current:   abef
    //
    // Live runs:
    //   [0,2) delta 0
    //   [4,6) delta -2
    //
    // toCurrentOffset(3) should return 2, the current start of original [4,6).
    int successorCurrentStart = currentLength;
    boolean hasSuccessor = false;

    while (node != null) {
      accumulatedDelta += node.delta;

      if (offsetInVersion0 < node.start0) {
        // Target is before this live run in version-0 space.
        // This node is the best successor seen so far.
        successorCurrentStart = node.start0 + accumulatedDelta;
        hasSuccessor = true;
        node = node.left;
      }
      else if (offsetInVersion0 >= node.end0) {
        // Target is after this live run.
        node = node.right;
      }
      else {
        // Target lies inside surviving original text.
        return offsetInVersion0 + accumulatedDelta;
      }
    }

    // Not found => the offset is inside deleted original text, or it is the EOF
    // boundary. Right-biased result is the next live run's current start, or
    // currentLength if there is no next live run.
    return hasSuccessor ? successorCurrentStart : currentLength;
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

    // Find the version-0 boundary before which the inserted text should appear.
    //
    // If offsetInCurrent is inside surviving original text, this returns the
    // corresponding original offset.
    //
    // If offsetInCurrent is inside already-inserted text, this returns the
    // version-0 boundary after that inserted gap.
    int boundary0 = toVersion0Offset(offsetInCurrent);

    // Split live original runs at boundary0.
    //
    // left:  all live runs strictly before boundary0
    // right: all live runs starting at or after boundary0
    Split split = splitByVersion0(root, boundary0);

    // Inserting current text before boundary0 shifts all surviving original text
    // after boundary0 by +length.
    //
    // The inserted text itself is not represented as a node. It is represented
    // as a current-coordinate gap between transformed live runs.
    Node shiftedRight = addDelta(split.right(), length);

    Node newRoot = concat(split.left(), shiftedRight);

    return new ModificationTreeImpl(
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
    Node newRoot = concatCoalescing(first.left(), shiftedRight);

    return new ModificationTreeImpl(
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
      CheckInfo info = checkNode(root, 0);

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

    checkBoundaryMappings();
  }

  /// A node represents one live original run `[start0, end0)`.
  ///
  /// Its current range is:
  ///
  /// `[start0 + effectiveDelta, end0 + effectiveDelta)`
  ///
  /// where:
  ///
  /// `effectiveDelta = sum(delta on path from root to this node)`
  private static final class Node {
    private final int start0;
    private final int end0;
    private final int delta;
    private final Node left;
    private final Node right;
    private final int height;

    private Node(int start0, int end0, int delta, Node left, Node right) {
      if (start0 >= end0) {
        throw new IllegalArgumentException(
          "Invalid live run: [" + start0 + ", " + end0 + ")"
        );
      }

      this.start0 = start0;
      this.end0 = end0;
      this.delta = delta;
      this.left = left;
      this.right = right;
      this.height = 1 + Math.max(height(left), height(right));
    }

    private int start0() {
      return start0;
    }

    private int end0() {
      return end0;
    }

    private int delta() {
      return delta;
    }

    private Node left() {
      return left;
    }

    private Node right() {
      return right;
    }

    private int height() {
      return height;
    }

    private Node addDelta(int deltaDiff) {
      if (deltaDiff == 0) {
        return this;
      }

      return new Node(start0, end0, delta + deltaDiff, left, right);
    }

    private static int height(Node node) {
      return node == null ? 0 : node.height;
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

  private static final class RemoveMax {
    private final Node rest;
    private final Node max;

    private RemoveMax(Node rest, Node max) {
      this.rest = rest;
      this.max = max;
    }

    private Node rest() {
      return rest;
    }

    private Node max() {
      return max;
    }
  }

  private static final class RemoveMin {
    private final Node min;
    private final Node rest;

    private RemoveMin(Node min, Node rest) {
      this.min = min;
      this.rest = rest;
    }

    private Node min() {
      return min;
    }

    private Node rest() {
      return rest;
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

  /// Add `deltaDiff` to a whole subtree.
  ///
  /// Since deltas are path-summed, changing the subtree root is enough.
  private static Node addDelta(Node node, int deltaDiff) {
    if (node == null || deltaDiff == 0) {
      return node;
    }

    return node.addDelta(deltaDiff);
  }

  /// Build a node from standalone child subtrees.
  ///
  /// The child roots already contain their standalone effective deltas.
  /// Once attached under this node, they inherit this node's delta, so their
  /// root deltas are adjusted by `-delta`.
  private static Node makeNode(
    int start0,
    int end0,
    int delta,
    Node leftStandalone,
    Node rightStandalone
  ) {
    return new Node(
      start0,
      end0,
      delta,
      addDelta(leftStandalone, -delta),
      addDelta(rightStandalone, -delta)
    );
  }

  private static Node nodeAlone(Node node) {
    return new Node(
      node.start0(),
      node.end0(),
      node.delta(),
      null,
      null
    );
  }

  private static Node standaloneLeft(Node node) {
    return addDelta(node.left(), node.delta());
  }

  private static Node standaloneRight(Node node) {
    return addDelta(node.right(), node.delta());
  }

  /// Split the tree by version-0 coordinate.
  ///
  /// Result:
  ///
  /// - `left`: all live original runs before `boundary0`
  /// - `right`: all live original runs at or after `boundary0`
  private static @NotNull Split splitByVersion0(Node node, int boundary0) {
    if (node == null) {
      return new Split(null, null);
    }

    if (boundary0 <= node.start0()) {
      Split splitLeft = splitByVersion0(standaloneLeft(node), boundary0);

      Node nodeAndRight = concat(
        nodeAlone(node),
        standaloneRight(node)
      );

      return new Split(
        splitLeft.left(),
        concat(splitLeft.right(), nodeAndRight)
      );
    }

    if (boundary0 >= node.end0()) {
      Split splitRight = splitByVersion0(standaloneRight(node), boundary0);

      Node leftAndNode = concat(
        standaloneLeft(node),
        nodeAlone(node)
      );

      return new Split(
        concat(leftAndNode, splitRight.left()),
        splitRight.right()
      );
    }

    // boundary0 is strictly inside this live run:
    //
    //     [start0, end0)
    //
    // becomes:
    //
    //     [start0, boundary0)
    //     [boundary0, end0)
    Node leftRun = new Node(
      node.start0(),
      boundary0,
      node.delta(),
      null,
      null
    );

    Node rightRun = new Node(
      boundary0,
      node.end0(),
      node.delta(),
      null,
      null
    );

    return new Split(
      concat(standaloneLeft(node), leftRun),
      concat(rightRun, standaloneRight(node))
    );
  }

  /// Concatenate two ordered standalone trees.
  private static Node concat(Node left, Node right) {
    if (left == null) {
      return right;
    }

    if (right == null) {
      return left;
    }

    int leftHeight = Node.height(left);
    int rightHeight = Node.height(right);

    if (leftHeight > rightHeight + 1) {
      Node newRight = concat(standaloneRight(left), right);

      return balance(makeNode(
        left.start0(),
        left.end0(),
        left.delta(),
        standaloneLeft(left),
        newRight
      ));
    }

    if (rightHeight > leftHeight + 1) {
      Node newLeft = concat(left, standaloneLeft(right));

      return balance(makeNode(
        right.start0(),
        right.end0(),
        right.delta(),
        newLeft,
        standaloneRight(right)
      ));
    }

    RemoveMax removed = removeMax(left);

    return balance(makeNode(
      removed.max().start0(),
      removed.max().end0(),
      removed.max().delta(),
      removed.rest(),
      right
    ));
  }

  /// Concatenate and merge adjacent boundary runs when possible.
  private static Node concatCoalescing(Node left, Node right) {
    if (left == null) {
      return right;
    }

    if (right == null) {
      return left;
    }

    RemoveMax leftRemoved = removeMax(left);
    RemoveMin rightRemoved = removeMin(right);

    Node leftMax = leftRemoved.max();
    Node rightMin = rightRemoved.min();

    if (canMerge(leftMax, rightMin)) {
      Node merged = new Node(
        leftMax.start0(),
        rightMin.end0(),
        leftMax.delta(),
        null,
        null
      );

      return concat(
        concat(leftRemoved.rest(), merged),
        rightRemoved.rest()
      );
    }

    return concat(
      concat(leftRemoved.rest(), leftMax),
      concat(rightMin, rightRemoved.rest())
    );
  }

  private static boolean canMerge(Node left, Node right) {
    return left.end0() == right.start0()
           && left.delta() == right.delta();
  }

  private static RemoveMax removeMax(Node node) {
    Node right = standaloneRight(node);

    if (right == null) {
      return new RemoveMax(
        standaloneLeft(node),
        nodeAlone(node)
      );
    }

    RemoveMax removed = removeMax(right);

    Node rebuilt = balance(makeNode(
      node.start0(),
      node.end0(),
      node.delta(),
      standaloneLeft(node),
      removed.rest()
    ));

    return new RemoveMax(rebuilt, removed.max());
  }

  private static RemoveMin removeMin(Node node) {
    Node left = standaloneLeft(node);

    if (left == null) {
      return new RemoveMin(
        nodeAlone(node),
        standaloneRight(node)
      );
    }

    RemoveMin removed = removeMin(left);

    Node rebuilt = balance(makeNode(
      node.start0(),
      node.end0(),
      node.delta(),
      removed.rest(),
      standaloneRight(node)
    ));

    return new RemoveMin(removed.min(), rebuilt);
  }

  private static Node balance(Node node) {
    if (node == null) {
      return null;
    }

    int balance = Node.height(node.left()) - Node.height(node.right());

    if (balance > 1) {
      Node left = standaloneLeft(node);

      if (Node.height(left.right()) > Node.height(left.left())) {
        left = rotateLeft(left);
      }

      Node rebuilt = makeNode(
        node.start0(),
        node.end0(),
        node.delta(),
        left,
        standaloneRight(node)
      );

      return rotateRight(rebuilt);
    }

    if (balance < -1) {
      Node right = standaloneRight(node);

      if (Node.height(right.left()) > Node.height(right.right())) {
        right = rotateRight(right);
      }

      Node rebuilt = makeNode(
        node.start0(),
        node.end0(),
        node.delta(),
        standaloneLeft(node),
        right
      );

      return rotateLeft(rebuilt);
    }

    return node;
  }

  private static Node rotateRight(Node node) {
    Node left = standaloneLeft(node);

    Node a = standaloneLeft(left);
    Node b = standaloneRight(left);
    Node c = standaloneRight(node);

    Node newRight = makeNode(
      node.start0(),
      node.end0(),
      node.delta(),
      b,
      c
    );

    return makeNode(
      left.start0(),
      left.end0(),
      left.delta(),
      a,
      newRight
    );
  }

  private static Node rotateLeft(Node node) {
    Node right = standaloneRight(node);

    Node a = standaloneLeft(node);
    Node b = standaloneLeft(right);
    Node c = standaloneRight(right);

    Node newLeft = makeNode(
      node.start0(),
      node.end0(),
      node.delta(),
      a,
      b
    );

    return makeNode(
      right.start0(),
      right.end0(),
      right.delta(),
      newLeft,
      c
    );
  }

  /// Invariant checking.
  private CheckInfo checkNode(Node node, int accumulatedDeltaBeforeNode) {
    if (node == null) {
      throw new IllegalStateException("checkNode called with null");
    }

    int effectiveDelta = accumulatedDeltaBeforeNode + node.delta();

    checkNodeOwnFields(node, effectiveDelta);
    checkNodeHeightAndBalance(node);

    int nodeCurrentStart = node.start0() + effectiveDelta;
    int nodeCurrentEnd = node.end0() + effectiveDelta;

    CheckInfo leftInfo = null;
    CheckInfo rightInfo = null;

    if (node.left() != null) {
      leftInfo = checkNode(node.left(), effectiveDelta);

      if (leftInfo.end0() > node.start0()) {
        throw new IllegalStateException(
          "left subtree overlaps node: leftEnd=" +
          leftInfo.end0() + ", nodeStart=" + node.start0()
        );
      }

      if (leftInfo.currentEnd() > nodeCurrentStart) {
        throw new IllegalStateException(
          "left subtree overlaps node in current coordinates: leftCurrentEnd=" +
          leftInfo.currentEnd() + ", nodeCurrentStart=" + nodeCurrentStart
        );
      }

      if (
        leftInfo.end0() == node.start0() &&
        leftInfo.lastEffectiveDelta() == effectiveDelta
      ) {
        throw new IllegalStateException(
          "mergeable adjacent runs remain between left subtree and node at version0 offset " +
          node.start0()
        );
      }
    }

    if (node.right() != null) {
      rightInfo = checkNode(node.right(), effectiveDelta);

      if (node.end0() > rightInfo.start0()) {
        throw new IllegalStateException(
          "node overlaps right subtree: nodeEnd=" +
          node.end0() + ", rightStart=" + rightInfo.start0()
        );
      }

      if (nodeCurrentEnd > rightInfo.currentStart()) {
        throw new IllegalStateException(
          "node overlaps right subtree in current coordinates: nodeCurrentEnd=" +
          nodeCurrentEnd + ", rightCurrentStart=" + rightInfo.currentStart()
        );
      }

      if (
        node.end0() == rightInfo.start0() &&
        effectiveDelta == rightInfo.firstEffectiveDelta()
      ) {
        throw new IllegalStateException(
          "mergeable adjacent runs remain between node and right subtree at version0 offset " +
          node.end0()
        );
      }
    }

    int subtreeStart0 = leftInfo == null ? node.start0() : leftInfo.start0();
    int subtreeEnd0 = rightInfo == null ? node.end0() : rightInfo.end0();

    int subtreeCurrentStart = leftInfo == null
                              ? nodeCurrentStart
                              : leftInfo.currentStart();

    int subtreeCurrentEnd = rightInfo == null
                            ? nodeCurrentEnd
                            : rightInfo.currentEnd();

    int runCount = 1
                   + (leftInfo == null ? 0 : leftInfo.runCount())
                   + (rightInfo == null ? 0 : rightInfo.runCount());

    int firstEffectiveDelta = leftInfo == null
                              ? effectiveDelta
                              : leftInfo.firstEffectiveDelta();

    int lastEffectiveDelta = rightInfo == null
                             ? effectiveDelta
                             : rightInfo.lastEffectiveDelta();

    return new CheckInfo(
      subtreeStart0,
      subtreeEnd0,
      subtreeCurrentStart,
      subtreeCurrentEnd,
      node.height(),
      runCount,
      firstEffectiveDelta,
      lastEffectiveDelta
    );
  }

  private void checkNodeOwnFields(Node node, int effectiveDelta) {
    if (node.start0() < 0) {
      throw new IllegalStateException("node.start0 < 0: " + node.start0());
    }

    if (node.end0() > version0Length) {
      throw new IllegalStateException(
        "node.end0 exceeds version0Length: " +
        node.end0() + " > " + version0Length
      );
    }

    if (node.start0() >= node.end0()) {
      throw new IllegalStateException(
        "invalid node range: [" + node.start0() + ", " + node.end0() + ")"
      );
    }

    int currentStart = node.start0() + effectiveDelta;
    int currentEnd = node.end0() + effectiveDelta;

    if (currentStart < 0) {
      throw new IllegalStateException(
        "node currentStart < 0: " + currentStart +
        ", node=[" + node.start0() + ", " + node.end0() + ")" +
        ", effectiveDelta=" + effectiveDelta
      );
    }

    if (currentEnd > currentLength) {
      throw new IllegalStateException(
        "node currentEnd exceeds currentLength: " +
        currentEnd + " > " + currentLength +
        ", node=[" + node.start0() + ", " + node.end0() + ")" +
        ", effectiveDelta=" + effectiveDelta
      );
    }

    if (currentStart >= currentEnd) {
      throw new IllegalStateException(
        "invalid current range: [" + currentStart + ", " + currentEnd + ")"
      );
    }
  }

  private static void checkNodeHeightAndBalance(Node node) {
    int leftHeight = Node.height(node.left());
    int rightHeight = Node.height(node.right());

    int expectedHeight = 1 + Math.max(leftHeight, rightHeight);

    if (node.height() != expectedHeight) {
      throw new IllegalStateException(
        "height mismatch for node [" +
        node.start0() + ", " + node.end0() + "): " +
        node.height() + " != " + expectedHeight
      );
    }

    int balance = leftHeight - rightHeight;

    if (balance < -1 || balance > 1) {
      throw new IllegalStateException(
        "AVL balance violation for node [" +
        node.start0() + ", " + node.end0() + "): " +
        "leftHeight=" + leftHeight +
        ", rightHeight=" + rightHeight
      );
    }
  }

  private void checkBoundaryMappings() {
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
}