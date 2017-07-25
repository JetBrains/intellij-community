package jetCheck;

import junit.framework.TestCase;

import java.util.function.Predicate;

/**
 * @author peter
 */
abstract class PropertyCheckerTestCase extends TestCase {

  protected <T> PropertyFalsified checkFails(PropertyChecker<T> checker, Predicate<T> predicate) {
    try {
      checker.shouldHold(predicate);
      throw new AssertionError("Can't falsify " + getName());
    }
    catch (PropertyFalsified e) {
      return e;
    }
  }

  protected <T> PropertyFailure<T> checkFalsified(Generator<T> generator, Predicate<T> predicate, int minimizationSteps) {
    PropertyFalsified e = checkFails(forAllStable(generator), predicate);
    //noinspection unchecked
    PropertyFailure<T> failure = (PropertyFailure<T>)e.getFailure();

    System.out.println(" " + getName());
    System.out.println("Value: " + e.getBreakingValue());
    System.out.println("Data: " + e.getData());
    assertEquals(minimizationSteps, failure.getTotalMinimizationExampleCount());
    assertEquals(e.getBreakingValue(), generator.getGeneratorFunction().apply(e.getData()));

    return failure;
  }

  protected static <T> PropertyChecker<T> forAllStable(Generator<T> generator) {
    return PropertyChecker.forAll(generator).withSeed(0);
  }
}
