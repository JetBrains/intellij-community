package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
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
}

class StructureNode extends StructureElement {
  final List<StructureElement> children;
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

    return isList() ? new RemoveListRange(this) : shrinkChild(0);
  }

  @Nullable
  ShrinkStep shrinkChild(int index) {
    for (; index < children.size(); index++) {
      ShrinkStep childShrink = children.get(index).shrink();
      if (childShrink != null) return wrapChildShrink(index, childShrink);
    }
    
    return shrinkRecursion();
  }

  @Nullable
  private ShrinkStep wrapChildShrink(int index, @Nullable ShrinkStep step) {
    if (step == null) return shrinkChild(index + 1);

    NodeId oldChild = children.get(index).id;

    return new ShrinkStep() {
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

      @NotNull
      @Override
      NodeId getNodeAfter() {
        return step.getNodeAfter();
      }

      @Override
      public String toString() {
        return "-" + step.toString();
      }
    };
  }

  private boolean isList() {
    if (children.size() > 1 &&
        children.get(0) instanceof IntData && ((IntData)children.get(0)).value >= children.size() - 1) {
      for (int i = 1; i < children.size(); i++) {
        if (!(children.get(i) instanceof StructureNode)) return false;
      }
      return true;
    }
    return false;
  }

  private void findChildrenWithGenerator(@NotNull Generator<?> generator, List<StructureNode> result) {
    for (StructureElement child : children) {
      if (child instanceof StructureNode) {
        Generator<?> childGen = child.id.generator;
        if (childGen != null && generator.getGeneratorFunction().equals(childGen.getGeneratorFunction())) {
          result.add((StructureNode)child);
        } else {
          ((StructureNode)child).findChildrenWithGenerator(generator, result);
        }
      }
    }
  }

  @Nullable
  private ShrinkStep shrinkRecursion() {
    if (id.generator != null) {
      List<StructureNode> sameGeneratorChildren = new ArrayList<>();
      findChildrenWithGenerator(id.generator, sameGeneratorChildren);
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
    return copy;
  }

  @Nullable
  @Override
  StructureElement findChildById(NodeId id) {
    if (id == this.id) return this;
    int index = indexOfChildContaining(id);
    return index < 0 ? null : children.get(index).findChildById(id);
  }

  private int indexOfChildContaining(NodeId id) {
    int i = 0;
    while (i < children.size() && children.get(i).id.number <= id.number) i++;
    return i - 1;
  }

  @Override
  public int hashCode() {
    return children.hashCode();
  }

  @Override
  public String toString() {
    String inner = children.stream().map(Object::toString).collect(Collectors.joining(", "));
    return isList() ? "[" + inner + "]" : "(" + inner + ")";
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
    return value == 0 ? null : tryInt(0, () -> null, this::tryNegation);
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
    return tryInt(divided, () -> divisionLoop(divided / 2), null);
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
  public String toString() {
    return String.valueOf(value);
  }

  @Override
  public int hashCode() {
    return value;
  }
}