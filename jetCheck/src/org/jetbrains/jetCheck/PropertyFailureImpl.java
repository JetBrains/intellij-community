package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    NodeId limit = null;
    while (true) {
      ShrinkStep lastSuccessfulShrink = shrinkIteration(limit);
      if (lastSuccessfulShrink == null) break;
      limit = lastSuccessfulShrink.getNodeAfter();
    }
  }

  private ShrinkStep shrinkIteration(NodeId limit) {
    ShrinkStep lastSuccessfulShrink = null;
    ShrinkStep step = minimized.data.shrink();
    while (step != null) {
      step = findSuccessfulShrink(step, limit);
      if (step != null) {
        lastSuccessfulShrink = step;
        step = step.onSuccess(minimized.data);
      }
    }
    return lastSuccessfulShrink;
  }

  @Nullable
  private ShrinkStep findSuccessfulShrink(ShrinkStep step, @Nullable NodeId limit) {
    List<CustomizedNode> combinatorial = new ArrayList<>();

    while (step != null) {
      if (limit != null && limit.number <= step.getNodeAfter().number) {
        break;
      }
      StructureNode node = step.apply(minimized.data);
      if (node != null && iteration.session.generatedHashes.add(node.hashCode())) {
        CombinatorialIntCustomizer customizer = new CombinatorialIntCustomizer();
        if (tryStep(node, customizer)) {
          return step;
        }
        CombinatorialIntCustomizer next = customizer.nextAttempt();
        if (next != null) {
          combinatorial.add(new CustomizedNode(next, step));
        }
      }

      step = step.onFailure();
    }
    return processDelayedCombinations(combinatorial);
  }

  @Nullable
  private ShrinkStep processDelayedCombinations(List<CustomizedNode> delayed) {
    Collections.sort(delayed);

    for (CustomizedNode customizedNode : delayed) {
      CombinatorialIntCustomizer customizer = customizedNode.customizer;
      while (customizer != null) {
        if (tryStep(customizedNode.step.apply(minimized.data), customizer)) {
          return customizedNode.step;
        }
        customizer = customizer.nextAttempt();
      }
    }
    return null;
  }

  private boolean tryStep(StructureNode node, CombinatorialIntCustomizer customizer) {
    try {
      iteration.session.notifier.shrinkAttempt(this, iteration);

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
    return false;
  }

  private static class CustomizedNode implements Comparable<CustomizedNode> {
    final CombinatorialIntCustomizer customizer;
    final ShrinkStep step;

    CustomizedNode(CombinatorialIntCustomizer customizer, ShrinkStep step) {
      this.customizer = customizer;
      this.step = step;
    }

    @Override
    public int compareTo(@NotNull CustomizedNode o) {
      return Integer.compare(customizer.countVariants(), o.customizer.countVariants());
    }
  }
}
