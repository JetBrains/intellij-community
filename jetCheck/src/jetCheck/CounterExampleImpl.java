package jetCheck;

import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

class CounterExampleImpl<T> implements PropertyFailure.CounterExample<T> {
  final StructureNode data;
  private final T value;
  @Nullable private final Throwable exception;

  private CounterExampleImpl(StructureNode data, T value, @Nullable Throwable exception) {
    this.data = data;
    this.value = value;
    this.exception = exception;
  }

  @Override
  public T getExampleValue() {
    return value;
  }

  @Nullable
  @Override
  public Throwable getExceptionCause() {
    return exception;
  }

  static <T> CounterExampleImpl<T> checkProperty(Predicate<T> property, T value, StructureNode node) {
    try {
      if (!property.test(value)) {
        return new CounterExampleImpl<>(node, value, null);
      }
    }
    catch (Throwable e) {
      return new CounterExampleImpl<>(node, value, e);
    }
    return null;
  }

}