package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.function.Predicate;

class ReplayDataStructure extends AbstractDataStructure {
  private final Iterator<StructureElement> iterator;
  private final IntCustomizer customizer;

  ReplayDataStructure(StructureNode node, int sizeHint, IntCustomizer customizer) {
    super(node, sizeHint);
    this.iterator = node.childrenIterator();
    this.customizer = customizer;
  }

  @Override
  public int drawInt(@NotNull IntDistribution distribution) {
    return customizer.suggestInt(nextChild(IntData.class), distribution);
  }

  @NotNull
  private <E extends StructureElement> E nextChild(Class<E> required) {
    if (!iterator.hasNext()) throw new CannotRestoreValue();
    Object next = iterator.next();
    if (!required.isInstance(next)) throw new CannotRestoreValue();
    //noinspection unchecked
    return (E)next;
  }

  @NotNull
  @Override
  DataStructure subStructure(@NotNull Generator<?> generator) {
    return new ReplayDataStructure(nextChild(StructureNode.class), childSizeHint(), customizer);
  }

  @Override
  public <T> T generateNonShrinkable(@NotNull Generator<T> generator) {
    return generate(generator);
  }

  @Override
  public <T> T generateConditional(@NotNull Generator<T> generator, @NotNull Predicate<T> condition) {
    T value = generate(generator);
    if (!condition.test(value)) throw new CannotRestoreValue();
    return value;
  }

  @Override
  public String toString() {
    return node.toString();
  }
}