// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.pom.tree.events.impl;

import com.intellij.lang.ASTNode;
import com.intellij.pom.tree.events.TreeChange;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks changes to the direct children of a single AST parent node.
 * <p>
 * On construction, snapshots the parent's current children and their text lengths.
 * When the change set is queried, {@link ChildrenDiff} lazily diffs the snapshot against
 * the parent's current children to produce a {@link ChangeInfoImpl} for each
 * added, removed, replaced, or content-changed child.
 * <p>
 * Implements {@link Comparable} to sort changes in document order for event firing.
 */
@ApiStatus.Internal
public class TreeChangeImpl implements TreeChange, Comparable<TreeChangeImpl> {
  private final @NotNull ASTNode myParent;
  private final @NotNull List<ASTNode> mySuperParents;
  private final @NotNull LinkedHashMap<ASTNode, Integer> myInitialLengths = new LinkedHashMap<>();
  private final @NotNull Set<ASTNode> myContentChangeChildren = new HashSet<>();
  private @Nullable Map<ASTNode, ChangeInfoImpl> myChanges; // cached value

  public TreeChangeImpl(@NotNull ASTNode parent) {
    myParent = parent;
    assert myParent.getPsi() != null : myParent.getElementType() + " of " + myParent.getClass();
    mySuperParents = JBIterable.generate(parent.getTreeParent(), ASTNode::getTreeParent).toList();
    for (ASTNode child : getCurrentChildren()) {
      myInitialLengths.put(child, child.getTextLength());
    }
  }

  @NotNull List<ASTNode> getSuperParents() {
    return mySuperParents;
  }

  private @NotNull JBIterable<ASTNode> getCurrentChildren() {
    return JBIterable.generate(myParent.getFirstChildNode(), ASTNode::getTreeNext);
  }

  @Override
  public int compareTo(@NotNull TreeChangeImpl o) {
    List<ASTNode> thisParents = ContainerUtil.reverse(getSuperParents());
    List<ASTNode> thatParents = ContainerUtil.reverse(o.getSuperParents());
    for (int i = 1; i <= thisParents.size() && i <= thatParents.size(); i++) {
      ASTNode thisParent = i < thisParents.size() ? thisParents.get(i) : myParent;
      ASTNode thatParent = i < thatParents.size() ? thatParents.get(i) : o.myParent;
      int result = compareNodePositions(thisParent, thatParent);
      if (result != 0) return result;
    }
    return 0;
  }

  private static int compareNodePositions(ASTNode node1, ASTNode node2) {
    if (node1 == node2) return 0;

    int o1 = node1.getStartOffsetInParent();
    int o2 = node2.getStartOffsetInParent();
    return o1 != o2 ? Integer.compare(o1, o2) : Integer.compare(getChildIndex(node1), getChildIndex(node2));
  }

  private static int getChildIndex(ASTNode e) {
    return ArrayUtil.indexOf(e.getTreeParent().getChildren(null), e);
  }

  int getLengthDelta() {
    return getAllChanges().values().stream().mapToInt(ChangeInfoImpl::getLengthDelta).sum();
  }

  void clearCache() {
    myChanges = null;
  }

  private Map<ASTNode, ChangeInfoImpl> getAllChanges() {
    Map<ASTNode, ChangeInfoImpl> changes = myChanges;
    if (changes == null) {
      changes = new ChildrenDiff().calcChanges();
      myChanges = changes;
    }
    return changes;
  }

  private class ChildrenDiff {
    LinkedHashSet<ASTNode> currentChildren = getCurrentChildren().addAllTo(new LinkedHashSet<>());
    Iterator<ASTNode> itOld = myInitialLengths.keySet().iterator();
    Iterator<ASTNode> itNew = currentChildren.iterator();
    ASTNode oldChild, newChild;
    int oldOffset = 0;
    LinkedHashMap<ASTNode, ChangeInfoImpl> result = new LinkedHashMap<>();

    void advanceOld() {
      oldOffset += oldChild == null ? 0 : myInitialLengths.get(oldChild);
      oldChild = itOld.hasNext() ? itOld.next() : null;
    }

    void advanceNew() {
      newChild = itNew.hasNext() ? itNew.next() : null;
    }

    Map<ASTNode, ChangeInfoImpl> calcChanges() {
      advanceOld();
      advanceNew();

      while (oldChild != null || newChild != null) {
        if (oldChild == newChild) {
          if (myContentChangeChildren.contains(oldChild)) {
            addChange(new ChangeInfoImpl(oldChild, oldChild, oldOffset, myInitialLengths.get(oldChild)));
          }
          advanceOld();
          advanceNew();
        }
        else {
          boolean oldDisappeared = oldChild != null && !currentChildren.contains(oldChild);
          boolean newAppeared = newChild != null && !myInitialLengths.containsKey(newChild);
          ChangeInfoImpl change = new ChangeInfoImpl(
            /*oldChild =*/ oldDisappeared ? oldChild : null,
            /*newChild =*/ newAppeared ? newChild : null,
            /*offset = */ oldOffset,
            /*oldLength =*/ oldDisappeared ? myInitialLengths.get(oldChild) : 0);
          addChange(change);

          if (oldDisappeared) {
            advanceOld();
          }
          if (newAppeared) {
            advanceNew();
          }
        }
      }

      return result;
    }

    private void addChange(ChangeInfoImpl change) {
      result.put(change.getAffectedChild(), change);
      oldOffset += change.getLengthDelta();
    }
  }

  public @NotNull ASTNode getChangedParent() {
    return myParent;
  }

  void fireEvents(@NotNull PsiFile file) {
    int start = myParent.getStartOffset();
    Collection<ChangeInfoImpl> changes = getAllChanges().values();
    if (ContainerUtil.exists(changes, c -> c.hasNoPsi())) {
      ChangeInfoImpl.childrenChanged(ChangeInfoImpl.createEvent(file, start), myParent, myParent.getTextLength() - getLengthDelta());
      return;
    }

    for (ChangeInfoImpl change : changes) {
      change.fireEvent(start, file, myParent);
    }
  }

  @Override
  public @NotNull ASTNode @NotNull [] getAffectedChildren() {
    return getAllChanges().keySet().toArray(ASTNode.EMPTY_ARRAY);
  }

  @Override
  public @NotNull ChangeInfoImpl getChangeByChild(@NotNull ASTNode child) {
    ChangeInfoImpl info = getAllChanges().get(child);
    if (info == null) {
      throw new IllegalArgumentException("No change found for child: " + child);
    }
    return info;
  }

  public @NotNull List<ASTNode> getInitialChildren() {
    return new ArrayList<>(myInitialLengths.keySet());
  }

  @Override
  public String toString() {
    return myParent + ": " + getAllChanges().values();
  }

  void appendChanges(@NotNull TreeChangeImpl next) {
    myContentChangeChildren.addAll(next.myContentChangeChildren);
    clearCache();
  }

  public void markChildChanged(@NotNull ASTNode child, int lengthDelta) {
    myContentChangeChildren.add(child);
    if (lengthDelta != 0) {
      myInitialLengths.computeIfPresent(child, (c, oldLength) -> oldLength - lengthDelta);
    }
    clearCache();
  }
}
