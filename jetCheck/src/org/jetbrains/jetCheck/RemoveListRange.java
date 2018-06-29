/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jetCheck;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author peter
 */
class RemoveListRange extends ShrinkStep {
  private final StructureNode node;
  private final int lastSuccessfulRemove;
  private final int start;
  private final int length;

  static RemoveListRange fromEnd(StructureNode node) {
    int likelyFailingSuffix = node.isIncompleteList() && node.children.size() > 2 ? 1 : 0;
    return new RemoveListRange(node,
                               node.children.size() - likelyFailingSuffix,
                               node.children.size() - likelyFailingSuffix - 1, 1);
  }

  private RemoveListRange(StructureNode node, int lastSuccessfulRemove, int start, int length) {
    this.node = node;
    this.lastSuccessfulRemove = lastSuccessfulRemove;
    this.start = start;
    this.length = length;
    assert start > 0;
    assert start + length <= node.children.size();
    assert lastSuccessfulRemove > 0;
    assert lastSuccessfulRemove <= node.children.size();
  }

  @Override
  List<?> getEqualityObjects() {
    return Arrays.asList(node.id, start, length);
  }

  @Nullable
  @Override
  StructureNode apply(StructureNode root) {
    int newSize = node.children.size() - length - 1;
    IntDistribution lengthDistribution = ((IntData)node.children.get(0)).distribution;
    if (!lengthDistribution.isValidValue(newSize)) return null;

    List<StructureElement> lessItems = new ArrayList<>(newSize + 1);
    lessItems.add(node.isIncompleteList() ? node.children.get(0) : new IntData(node.children.get(0).id, newSize, lengthDistribution));
    lessItems.addAll(node.children.subList(1, start));
    lessItems.addAll(node.children.subList(start + length, node.children.size()));
    StructureNode replacement = new StructureNode(node.id, lessItems);
    replacement.kind = StructureKind.LIST;
    return root.replace(node.id, replacement);
  }

  @Override
  ShrinkStep onFailure() {
    if (length > 1) {
      int end = start + length;
      return new RemoveListRange(node, lastSuccessfulRemove, end - (length / 2), length / 2);
    }

    int newEnd = start == 1 ? node.children.size() : start;
    if (newEnd == lastSuccessfulRemove) return node.shrinkChild(node.children.size() - 1);
    return new RemoveListRange(node, lastSuccessfulRemove, newEnd - 1, 1);
  }

  @Nullable
  @Override
  ShrinkStep onSuccess(StructureNode smallerRoot) {
    if (length == node.children.size() - 1) return null;

    StructureNode inheritor = (StructureNode)Objects.requireNonNull(smallerRoot.findChildById(node.id));
    if (start == 1) return fromEnd(inheritor);
    
    int newLength = Math.min(length * 2, start - 1);
    return new RemoveListRange(inheritor, start, start - newLength, newLength);
  }

  @Override
  public String toString() {
    return "RemoveListRange{" +
           "last=" + lastSuccessfulRemove +
           ", start=" + start +
           ", length=" + length +
           ", node=" + node.id + ": " + node +
           '}';
  }
}
