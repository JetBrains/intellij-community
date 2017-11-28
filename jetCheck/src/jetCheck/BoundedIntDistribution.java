package jetCheck;

import java.util.Random;
import java.util.function.ToIntFunction;

public class BoundedIntDistribution implements IntDistribution {
  public static final IntDistribution ALL_INTS = IntDistribution.uniform(Integer.MIN_VALUE, Integer.MAX_VALUE);
  private final int min;
  private final int max;
  private final ToIntFunction<Random> producer;

  public BoundedIntDistribution(int min, int max, ToIntFunction<Random> producer) {
    if (min > max) throw new IllegalArgumentException(min + ">" + max);
    this.min = min;
    this.max = max;
    this.producer = producer;
  }

  @Override
  public int generateInt(Random random) {
    int i = producer.applyAsInt(random);
    if (i < min || i > max) {
      throw new IllegalStateException("Int out of bounds produced by " + producer + ": " + i + " not in [" + min + ", " + max + "]");
    }
    return i;
  }

  @Override
  public boolean isValidValue(int i) {
    return i >= min && i <= max;
  }

  public static BoundedIntDistribution bound(int min, int max, IntDistribution distribution) {
    return new BoundedIntDistribution(min, max, random -> Math.min(Math.max(distribution.generateInt(random), min), max)) {
      @Override
      public boolean isValidValue(int i) {
        return super.isValidValue(i) && distribution.isValidValue(i);
      }
    };
  }
}