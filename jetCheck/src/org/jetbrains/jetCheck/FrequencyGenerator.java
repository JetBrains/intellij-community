package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author peter
 */
public class FrequencyGenerator<T> extends Generator<T> {
  private final List<WeightedGenerator<T>> alternatives;

  FrequencyGenerator(int weight1, Generator<? extends T> alternative1,
                     int weight2, Generator<? extends T> alternative2) {
    this(weightedGenerators(weight1, alternative1, weight2, alternative2));
  }
  
  private FrequencyGenerator(List<WeightedGenerator<T>> alternatives) {
    super(frequencyFunction(alternatives));
    this.alternatives = alternatives;
  }
  
  FrequencyGenerator<T> with(int weight, Generator<? extends T> alternative) {
    List<WeightedGenerator<T>> alternatives = new ArrayList<>(this.alternatives);
    alternatives.add(new WeightedGenerator<>(weight, alternative));
    return new FrequencyGenerator<>(alternatives);
  }

  @NotNull
  private static <T> Function<DataStructure, T> frequencyFunction(List<WeightedGenerator<T>> alternatives) {
    List<Integer> weights = alternatives.stream().map(w -> w.weight).collect(Collectors.toList());
    IntDistribution distribution = IntDistribution.frequencyDistribution(weights);
    return data -> data.generate(alternatives.get(data.drawInt(distribution)).generator);
  }

  @NotNull
  private static <T> List<WeightedGenerator<T>> weightedGenerators(int weight1, Generator<? extends T> alternative1,
                                                                   int weight2, Generator<? extends T> alternative2) {
    List<WeightedGenerator<T>> alternatives = new ArrayList<>();
    alternatives.add(new WeightedGenerator<>(weight1, alternative1));
    alternatives.add(new WeightedGenerator<>(weight2, alternative2));
    return alternatives;
  }

  private static class WeightedGenerator<T> {
    final int weight;
    final Generator<? extends T> generator;

    WeightedGenerator(int weight, Generator<? extends T> generator) {
      this.weight = weight;
      this.generator = generator;
    }
  }
}
