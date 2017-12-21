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
    minimized.data.shrink(new ShrinkContext() {
      @NotNull
      @Override
      StructureNode getCurrentMinimalRoot() {
        return minimized.data;
      }

      @Override
      boolean tryReplacement(@NotNull NodeId replacedId, @NotNull StructureElement replacement) {
        StructureNode node = minimized.data.replace(replacedId, replacement);
        if (!iteration.session.generatedHashes.add(node.hashCode())) return false;

        CombinatorialIntCustomizer customizer = new CombinatorialIntCustomizer();
        while (customizer != null) {
          try {
            iteration.session.notifier.shrinkAttempt(PropertyFailureImpl.this, iteration);

            totalSteps++;
            T value = iteration.generateValue(new ReplayDataStructure(node, iteration.sizeHint, customizer));
            CounterExampleImpl<T> example = CounterExampleImpl.checkProperty(iteration, value, customizer.writeChanges(node));
            if (example != null) {
              minimized = example;
              successfulSteps++;
              return true;
            }
          }
          catch (CannotRestoreValue ignored) {
          }
          customizer = customizer.nextAttempt();
        }
        return false;
      }
    });
  }
}
