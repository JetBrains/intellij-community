package jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class PropertyFailureImpl<T> implements PropertyFailure<T> {
  private final CounterExampleImpl<T> initial;
  private CounterExampleImpl<T> minimized;
  private int totalSteps;
  private int successfulSteps;
  final Iteration<T> iteration;
  private Throwable stoppingReason;

  PropertyFailureImpl(@NotNull CounterExampleImpl<T> initial, Iteration<T> iteration) {
    this.initial = initial;
    this.minimized = initial;
    this.iteration = iteration;
    try {
      shrink();
    }
    catch (Throwable e) {
      stoppingReason = e;
    }
  }

  @NotNull
  @Override
  public CounterExampleImpl<T> getFirstCounterExample() {
    return initial;
  }

  @NotNull
  @Override
  public CounterExampleImpl<T> getMinimalCounterexample() {
    return minimized;
  }

  @Nullable
  @Override
  public Throwable getStoppingReason() {
    return stoppingReason;
  }

  @Override
  public int getTotalMinimizationExampleCount() {
    return totalSteps;
  }

  @Override
  public int getMinimizationStageCount() {
    return successfulSteps;
  }

  @Override
  public int getIterationNumber() {
    return iteration.iterationNumber;
  }

  @Override
  public long getIterationSeed() {
    return iteration.iterationSeed;
  }

  @Override
  public long getGlobalSeed() {
    return iteration.session.globalSeed;
  }

  @Override
  public int getSizeHint() {
    return iteration.sizeHint;
  }

  private void shrink() {
    minimized.data.shrink(node -> {
      if (!iteration.session.generatedHashes.add(node.hashCode())) return false;

      iteration.session.notifier.shrinkAttempt(this, iteration);

      try {
        T value = iteration.session.generator.getGeneratorFunction().apply(new ReplayDataStructure((StructureNode)node, iteration.sizeHint));
        totalSteps++;
        CounterExampleImpl<T> example = CounterExampleImpl.checkProperty(iteration.session.property, value, (StructureNode)node);
        if (example != null) {
          minimized = example;
          successfulSteps++;
          return true;
        }
        return false;
      }
      catch (CannotRestoreValue e) {
        return false;
      }

    });
  }
}
