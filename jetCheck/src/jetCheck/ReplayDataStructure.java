package jetCheck;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.function.Predicate;

class ReplayDataStructure extends AbstractDataStructure {
  private final Iterator<StructureElement> iterator;

  public ReplayDataStructure(StructureNode node, int sizeHint) {
    super(node, sizeHint);
    this.iterator = node.childrenIterator();
  }

  @Override
  public int drawInt(@NotNull IntDistribution distribution) {
    int value = nextChild(IntData.class).value;
    if (!distribution.isValidValue(value)) throw new CannotRestoreValue();
    return value;
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
  public DataStructure subStructure() {
    return new ReplayDataStructure(nextChild(StructureNode.class), childSizeHint());
  }

  @Override
  public <T> T generateNonShrinkable(@NotNull Generator<T> generator) {
    return generator.generateValue(this);
  }

  @Override
  public <T> T generateConditional(@NotNull Generator<T> generator, @NotNull Predicate<T> condition) {
    T value = generator.generateValue(this);
    if (!condition.test(value)) throw new CannotRestoreValue();
    return value;
  }

  @Override
  public String toString() {
    return node.toString();
  }
}