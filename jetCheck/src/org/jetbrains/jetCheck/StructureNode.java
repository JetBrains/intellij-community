package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author peter
 */
abstract class StructureElement {
  final NodeId id;

  StructureElement(@NotNull NodeId id) {
    this.id = id;
  }

  @Nullable
  abstract ShrinkStep shrink();

  @NotNull
  abstract StructureElement replace(NodeId id, StructureElement replacement);

  @Nullable
  abstract StructureElement findChildById(NodeId id);
  
  abstract void serialize(DataOutputStream out) throws IOException;
}

class StructureNode extends StructureElement {
  final List<StructureElement> children;
  @NotNull StructureKind kind = StructureKind.GENERIC;
  boolean shrinkProhibited;

  StructureNode(NodeId id) {
    this(id, new ArrayList<>());
  }

  StructureNode(NodeId id, List<StructureElement> children) {
    super(id);
    this.children = children;
  }

  Iterator<StructureElement> childrenIterator() {
    return children.iterator();
  }

  void addChild(StructureElement child) {
    children.add(child);
  }

  StructureNode subStructure(@NotNull Generator<?> generator) {
    StructureNode e = new StructureNode(id.childId(generator));
    addChild(e);
    return e;
  }

  void removeLastChild(StructureNode node) {
    if (children.isEmpty() || children.get(children.size() - 1) != node) {
      throw new IllegalStateException("Last sub-structure changed");
    }
    children.remove(children.size() - 1);
  }

  @Nullable
  @Override
  ShrinkStep shrink() {
    if (shrinkProhibited) return null;

    return kind == StructureKind.LIST && children.size() > 1 ? RemoveListRange.fromEnd(this) : shrinkChild(children.size() - 1);
  }

  @Nullable
  ShrinkStep shrinkChild(int index) {
    int minIndex = kind == StructureKind.GENERIC ? 0 : 1;
    for (; index >= minIndex; index--) {
      ShrinkStep childShrink = children.get(index).shrink();
      if (childShrink != null) return wrapChildShrink(index, childShrink);
    }
    
    return shrinkRecursion();
  }

  @Nullable
  private ShrinkStep wrapChildShrink(int index, @Nullable ShrinkStep step) {
    if (step == null) return shrinkChild(index - 1);

    NodeId oldChild = children.get(index).id;

    return new ShrinkStep() {

      @Override
      List<?> getEqualityObjects() {
        return Collections.singletonList(step);
      }

      @Nullable
      @Override
      StructureNode apply(StructureNode root) {
        return step.apply(root);
      }

      @Override
      ShrinkStep onSuccess(StructureNode smallerRoot) {
        StructureNode inheritor = (StructureNode)Objects.requireNonNull(smallerRoot.findChildById(id));
        assert inheritor.children.size() == children.size();
        if (inheritor.children.get(index).id != oldChild) {
          return inheritor.shrink();
        }
        
        return inheritor.wrapChildShrink(index, step.onSuccess(smallerRoot));
      }

      @Override
      ShrinkStep onFailure() {
        return wrapChildShrink(index, step.onFailure());
      }

      @Override
      public String toString() {
        return "-" + step.toString();
      }
    };
  }

  boolean isIncompleteList() {
    return ((IntData)children.get(0)).value > children.size() - 1;
  }

  private void findChildrenWithGenerator(int generatorHash, List<StructureNode> result) {
    for (StructureElement child : children) {
      if (child instanceof StructureNode) {
        Integer childGen = child.id.generatorHash;
        if (childGen != null && generatorHash == childGen.intValue()) {
          result.add((StructureNode)child);
        } else {
          ((StructureNode)child).findChildrenWithGenerator(generatorHash, result);
        }
      }
    }
  }

  @Nullable
  private ShrinkStep shrinkRecursion() {
    if (id.generatorHash != null) {
      List<StructureNode> sameGeneratorChildren = new ArrayList<>();
      findChildrenWithGenerator(id.generatorHash, sameGeneratorChildren);
      return tryReplacing(sameGeneratorChildren, 0);
    }
    
    return null;
  }

  @Nullable
  private ShrinkStep tryReplacing(List<StructureNode> candidates, int index) {
    if (index < candidates.size()) {
      StructureNode replacement = candidates.get(index);
      return ShrinkStep.create(id, replacement, __ -> replacement.shrink(), () -> tryReplacing(candidates, index + 1));
    }
    return null;
  }

  @NotNull
  @Override
  StructureNode replace(NodeId id, StructureElement replacement) {
    if (id == this.id) {
      return (StructureNode)replacement;
    }
    
    if (children.isEmpty()) return this;

    int index = indexOfChildContaining(id);
    StructureElement oldChild = children.get(index);
    StructureElement newChild = oldChild.replace(id, replacement);
    if (oldChild == newChild) return this;

    List<StructureElement> newChildren = new ArrayList<>(this.children);
    newChildren.set(index, newChild);
    StructureNode copy = new StructureNode(this.id, newChildren);
    copy.shrinkProhibited = this.shrinkProhibited;
    copy.kind = this.kind;
    return copy;
  }

  @Nullable
  @Override
  StructureElement findChildById(NodeId id) {
    if (id == this.id) return this;
    int index = indexOfChildContaining(id);
    return index < 0 ? null : children.get(index).findChildById(id);
  }

  @Override
  void serialize(DataOutputStream out) throws IOException {
    for (StructureElement child : children) {
      child.serialize(out);
    }
  }

  private int indexOfChildContaining(NodeId id) {
    int i = 0;
    while (i < children.size() && children.get(i).id.number <= id.number) i++;
    return i - 1;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof StructureNode && children.equals(((StructureNode)obj).children);
  }

  @Override
  public int hashCode() {
    return children.hashCode();
  }

  @Override
  public String toString() {
    String inner = children.stream().map(Object::toString).collect(Collectors.joining(", "));
    switch (kind) {
      case LIST: return "[" + inner + "]";
      case CHOICE: return "?(" + inner + ")";
      default: return "(" + inner + ")";
    }
  }

}

class IntData extends StructureElement {
  final int value;
  final IntDistribution distribution;

  IntData(NodeId id, int value, IntDistribution distribution) {
    super(id);
    this.value = value;
    this.distribution = distribution;
  }

  @Nullable
  @Override
  ShrinkStep shrink() {
    if (value == 0) return null;

    int minValue = 0;
    if (distribution instanceof BoundedIntDistribution) {
      minValue = Math.max(minValue, ((BoundedIntDistribution)distribution).getMin());
    }
    return tryInt(minValue, () -> null, this::tryNegation);
  }

  private ShrinkStep tryNegation() {
    if (value < 0) {
      return tryInt(-value, () -> divisionLoop(-value), () -> divisionLoop(value));
    }
    return divisionLoop(value);
  }

  private ShrinkStep divisionLoop(int value) {
    if (value == 0) return null;
    int divided = value / 2;
    return tryInt(divided, () -> divisionLoop(divided), null);
  }

  private ShrinkStep tryInt(int value, @NotNull Supplier<ShrinkStep> success, @Nullable Supplier<ShrinkStep> fail) {
    return distribution.isValidValue(value) ? ShrinkStep.create(id, new IntData(id, value, distribution), __ -> success.get(), fail) : null;
  }

  @NotNull
  @Override
  IntData replace(NodeId id, StructureElement replacement) {
    return this.id == id ? (IntData)replacement : this;
  }

  @Nullable
  @Override
  StructureElement findChildById(NodeId id) {
    return id == this.id ? this : null;
  }

  @Override
  void serialize(DataOutputStream out) throws IOException {
    DataSerializer.writeINT(out, value);
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof IntData && value == ((IntData)obj).value;
  }

  @Override
  public int hashCode() {
    return value;
  }
}

enum StructureKind {
  GENERIC, LIST, CHOICE
}