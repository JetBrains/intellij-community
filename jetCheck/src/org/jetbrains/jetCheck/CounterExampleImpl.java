package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class CounterExampleImpl<T> implements PropertyFailure.CounterExample<T> {
  final StructureNode data;
  private final T value;
  @Nullable private final Throwable exception;
  private final Iteration<T> iteration;

  private CounterExampleImpl(StructureNode data, T value, @Nullable Throwable exception, Iteration<T> iteration) {
    this.data = data;
    this.value = value;
    this.exception = exception;
    this.iteration = iteration;
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

  @NotNull
  @Override
  public CounterExampleImpl<T> replay() {
    T value = iteration.generateValue(createReplayData());
    CounterExampleImpl<T> example = checkProperty(iteration, value, data);
    return example != null ? example : 
           new CounterExampleImpl<>(data, value, new IllegalStateException("Replaying failure is unexpectedly successful!"), iteration);
  }

  ReplayDataStructure createReplayData() {
    return new ReplayDataStructure(data, iteration.sizeHint, IntCustomizer::checkValidInt);
  }

  static <T> CounterExampleImpl<T> checkProperty(Iteration<T> iteration, T value, StructureNode node) {
    try {
      if (!iteration.session.property.test(value)) {
        return new CounterExampleImpl<>(node, value, null, iteration);
      }
    }
    catch (Throwable e) {
      return new CounterExampleImpl<>(node, value, e, iteration);
    }
    return null;
  }

}