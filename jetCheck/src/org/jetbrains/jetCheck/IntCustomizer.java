// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jetCheck;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author peter
 */

interface IntCustomizer {
  int suggestInt(IntData data, IntDistribution currentDistribution);

  static int checkValidInt(IntData data, IntDistribution currentDistribution) {
    int value = data.value;
    if (!currentDistribution.isValidValue(value)) throw new CannotRestoreValue();
    return value;
  }

}

class CombinatorialIntCustomizer implements IntCustomizer {
  private final LinkedHashMap<NodeId, Set<Integer>> valuesToTry;
  private final Map<NodeId, Integer> currentCombination;
  private final Map<NodeId, IntDistribution> changedDistributions = new HashMap<>();

  CombinatorialIntCustomizer() {
    this(new LinkedHashMap<>(), new HashMap<>());
  }

  private CombinatorialIntCustomizer(LinkedHashMap<NodeId, Set<Integer>> valuesToTry, Map<NodeId, Integer> currentCombination) {
    this.valuesToTry = valuesToTry;
    this.currentCombination = currentCombination;
  }

  public int suggestInt(IntData data, IntDistribution currentDistribution) {
    if (data.distribution instanceof BoundedIntDistribution &&
        currentDistribution instanceof BoundedIntDistribution &&
        registerDifferentRange(data, (BoundedIntDistribution)currentDistribution, (BoundedIntDistribution)data.distribution)) {
      return suggestCombinatorialVariant(data, currentDistribution);
    }
    return IntCustomizer.checkValidInt(data, currentDistribution);
  }

  private int suggestCombinatorialVariant(IntData data, IntDistribution currentDistribution) {
    int value = currentCombination.computeIfAbsent(data.id, __ -> valuesToTry.get(data.id).iterator().next());
    if (currentDistribution.isValidValue(value)) {
      return value;
    }

    throw new CannotRestoreValue();
  }

  private boolean registerDifferentRange(IntData data, BoundedIntDistribution current, BoundedIntDistribution original) {
    if (currentCombination.containsKey(data.id)) {
      changedDistributions.put(data.id, current);
      return true;
    }

    if (original.getMax() != current.getMax() || original.getMin() != current.getMin()) {
      LinkedHashSet<Integer> possibleValues = getPossibleValues(data, current, original);
      if (!possibleValues.isEmpty()) {
        assert !valuesToTry.containsKey(data.id);
        valuesToTry.put(data.id, possibleValues);
        changedDistributions.put(data.id, current);
        return true;
      }
    }
    return false;
  }

  private LinkedHashSet<Integer> getPossibleValues(IntData data, BoundedIntDistribution current, BoundedIntDistribution original) {
    List<Integer> possibleValues = new ArrayList<>();
    int fromStart = data.value - original.getMin();
    int fromEnd = original.getMax() - data.value;

    int sameDistanceFromStart = current.getMin() + fromStart;
    int sameDistanceFromEnd = current.getMax() - fromEnd;

    if (!tooManyCombinations()) {
      if (fromStart < fromEnd) {
        possibleValues.add(sameDistanceFromStart);
        possibleValues.add(sameDistanceFromEnd);
      } else {
        possibleValues.add(sameDistanceFromEnd);
        possibleValues.add(sameDistanceFromStart);
      }
    }
    possibleValues.add(data.value);
    
    return possibleValues.stream()
                         .map(value -> Math.min(Math.max(value, current.getMin()), current.getMax()))
                         .filter(current::isValidValue)
                         .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private boolean tooManyCombinations() {
    return valuesToTry.values().stream().filter(s -> s.size() > 1).count() > 3;
  }

  @Nullable
  CombinatorialIntCustomizer nextAttempt() {
    Map<NodeId, Integer> nextCombination = new HashMap<>(currentCombination);
    for (Map.Entry<NodeId, Set<Integer>> entry : valuesToTry.entrySet()) {
      List<Integer> possibleValues = new ArrayList<>(entry.getValue());
      Integer usedValue = currentCombination.get(entry.getKey());
      int index = possibleValues.indexOf(usedValue);
      if (index < possibleValues.size() - 1) {
        // found a position which can be incremented by 1
        nextCombination.put(entry.getKey(), possibleValues.get(index + 1));
        return new CombinatorialIntCustomizer(valuesToTry, nextCombination);
      }
      // digit overflow in this position, so zero it and try incrementing the next one
      nextCombination.put(entry.getKey(), possibleValues.get(0));
    }
    return null;
  }

  StructureNode writeChanges(StructureNode node) {
    StructureNode result = node;
    for (Map.Entry<NodeId, IntDistribution> entry : changedDistributions.entrySet()) {
      NodeId id = entry.getKey();
      result = result.replace(id, new IntData(id, currentCombination.get(id), entry.getValue()));
    }
    return result;
  }

  int countVariants() {
    return valuesToTry.values().stream().mapToInt(Set::size).reduce(1, (a, b) -> a*b);
  }
}