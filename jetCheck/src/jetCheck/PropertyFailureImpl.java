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
    ShrinkStep step = minimized.data.shrink();
    eachShrink: 
    while (step != null) {
      StructureNode node = step.apply(minimized.data);
      if (node == null || !iteration.session.generatedHashes.add(node.hashCode())) {
        step = step.onFailure();
        continue;
      }

      CombinatorialIntCustomizer customizer = new CombinatorialIntCustomizer();
      while (customizer != null) {
        try {
          iteration.session.notifier.shrinkAttempt(this, iteration);

          totalSteps++;
          T value = iteration.generateValue(new ReplayDataStructure(node, iteration.sizeHint, customizer));
          CounterExampleImpl<T> example = CounterExampleImpl.checkProperty(iteration, value, customizer.writeChanges(node));
          if (example != null) {
            minimized = example;
            successfulSteps++;
            step = step.onSuccess(minimized.data);
            continue eachShrink;
          }
        }
        catch (CannotRestoreValue ignored) {
        }
        customizer = customizer.nextAttempt();
      }
      step = step.onFailure();
    }
  }
}
