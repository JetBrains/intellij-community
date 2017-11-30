package jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author peter
 */
abstract class StructureElement {
  final NodeId id;

  StructureElement(@NotNull NodeId id) {
    this.id = id;
  }

  abstract void shrink(ShrinkContext suitable);

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

  StructureNode subStructure() {
    StructureNode e = new StructureNode(id.childId());
    addChild(e);
    return e;
  }

  void removeLastChild(StructureNode node) {
    if (children.isEmpty() || children.get(children.size() - 1) != node) {
      throw new IllegalStateException("Last sub-structure changed");
    }
    children.remove(children.size() - 1);
  }

  @Override
  void shrink(ShrinkContext context) {
    if (shrinkProhibited) return;

    StructureNode node = this;
    boolean isList = isList();
    if (isList) {
      node = shrinkList(context, node);
    }

    for (int i = isList ? 1 : 0; i < node.children.size(); i++) {
      node.children.get(i).shrink(context);
    }

    StructureElement latest = context.getCurrentMinimalRoot().findChildById(this.id);
    if (latest instanceof StructureNode) {
      ((StructureNode)latest).shrinkAlternativeListRecursion(context);
    }
  }

  private static StructureNode shrinkList(ShrinkContext context, StructureNode node) {
    int limit = node.children.size();
    while (limit > 0) {
      int start = 1;
      int length = 1;
      int lastSuccessfulRemove = -1;
      while (start < limit && start < node.children.size()) {
        // remove last items from the end first to decrease the number of variants in CombinatorialIntCustomizer
        StructureNode withRemovedRange = node.removeRange(node.children, node.children.size() - start - length + 1, length);
        if (withRemovedRange != null && context.tryReplacement(node.id, withRemovedRange)) {
          node = (StructureNode)Objects.requireNonNull(context.getCurrentMinimalRoot().findChildById(node.id));
          length = Math.min(length * 2, node.children.size() - start);
          lastSuccessfulRemove = start;
        } else {
          if (length > 1) {
            length /= 2;
          } else {
            start++;
          }
        }
      }
      limit = lastSuccessfulRemove;
    }
    return node;
  }

  @Nullable
  private StructureNode removeRange(List<StructureElement> listChildren, int start, int length) {
    int newSize = listChildren.size() - length - 1;
    IntDistribution lengthDistribution = ((IntData)children.get(0)).distribution;
    if (!lengthDistribution.isValidValue(newSize)) return null;

    List<StructureElement> lessItems = new ArrayList<>(newSize + 1);
    lessItems.add(new IntData(children.get(0).id, newSize, lengthDistribution));
    lessItems.addAll(listChildren.subList(1, start));
    lessItems.addAll(listChildren.subList(start + length, listChildren.size()));
    return new StructureNode(id, lessItems);
  }

  private boolean isList() {
    if (!children.isEmpty() &&
        children.get(0) instanceof IntData && ((IntData)children.get(0)).value == children.size() - 1) {
      for (int i = 1; i < children.size(); i++) {
        if (!(children.get(i) instanceof StructureNode)) return false;
      }
      return true;
    }
    return false;
  }

  private void shrinkAlternativeListRecursion(ShrinkContext context) {
    if (seemsAlternative(children)) {
      StructureElement child1 = deparenthesize(children.get(1));
      if (child1 instanceof StructureNode && ((StructureNode)child1).isList() && ((StructureNode)child1).children.size() == 2) {
        StructureElement singleListElement = deparenthesize(((StructureNode)child1).children.get(1));
        if (singleListElement instanceof StructureNode && seemsAlternative(((StructureNode)singleListElement).children)) {
          context.tryReplacement(id, singleListElement);
        }
      }
    }
  }

  private static StructureElement deparenthesize(StructureElement e) {
    while (e instanceof StructureNode && ((StructureNode)e).children.size() == 1) {
      e = ((StructureNode)e).children.get(0);
    }
    return e;
  }

  private static boolean seemsAlternative(List<StructureElement> children) {
    return children.size() == 2 && deparenthesize(children.get(0)) instanceof IntData && children.get(1) instanceof StructureNode;
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
    return "(" + children.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
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

  @Override
  void shrink(ShrinkContext context) {
    if (value == 0 || tryInt(0, context)) return;

    int value = this.value;
    if (value < 0 && tryInt(-value, context)) {
      value = -value;
    }
    while (value != 0 && tryInt(value / 2, context)) {
      value /= 2;
    }
  }

  private boolean tryInt(int value, ShrinkContext context) {
    return distribution.isValidValue(value) && context.tryReplacement(id, new IntData(id, value, distribution));
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