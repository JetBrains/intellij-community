package org.jetbrains.jetCheck;

import java.util.List;
import java.util.Random;

/**
 * Used for generating random int values with custom distribution, and ensuring that minimized integer values don't violate that distribution. 
 */
public interface IntDistribution {
  /** Returns an int value distributed in the needed fashion using the given Random */ 
  int generateInt(Random random);

  /** @return true if the given value is valid for this distribution */
  boolean isValidValue(int i);
  
  /**
   * This distribution returns an integer uniformly distributed between {@code min} and {@code max} (both ends inclusive).
   */
  static IntDistribution uniform(int min, int max) {
    return new BoundedIntDistribution(min, max, r -> {
      if (min == max) return min;
      
      int i = r.nextInt();
      return i >= min && i <= max ? i : Math.abs(i) % (max - min + 1) + min;
    });
  }

  /**
   * Geometric distribution ("number of failures until first success") with a given mean
   */
  static IntDistribution geometric(int mean) {
    double p = 1.0 / (mean + 1);
    return new IntDistribution() {
      @Override
      public int generateInt(Random random) {
        double u = random.nextDouble();
        return (int) (Math.log(u) / Math.log(1 - p));
      }

      @Override
      public boolean isValidValue(int i) {
        return i >= 0;
      }
    };
  }

  /**
   * The distribution for numbers in {@code [0, ..., weights.size()-1]} range, where the probability of {@code i}
   * is equal to {@code weights.get(i)/sum(weights)}.
   */
  static IntDistribution frequencyDistribution(List<Integer> weights) {
    if (weights.isEmpty()) throw new IllegalArgumentException("No alternatives to choose from");
    
    int sum = weights.stream().reduce(0, (a, b) -> a + b);
    return new BoundedIntDistribution(0, weights.size() - 1, r -> {
      int value = r.nextInt(sum);
      for (int i = 0; i < weights.size(); i++) {
        value -= weights.get(i);
        if (value < 0) return i;
      }
      throw new IllegalArgumentException();
    });
  }
}
