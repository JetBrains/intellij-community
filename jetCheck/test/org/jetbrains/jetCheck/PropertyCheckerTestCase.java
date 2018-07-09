/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jetCheck;

import junit.framework.TestCase;

import java.util.function.Predicate;

/**
 * @author peter
 */
abstract class PropertyCheckerTestCase extends TestCase {
  @SuppressWarnings("deprecation") 
  static final PropertyChecker.Parameters STABLE = PropertyChecker.customized().withSeed(0);

  protected <T> PropertyFalsified checkFails(PropertyChecker.Parameters parameters, Generator<T> checker, Predicate<T> predicate) {
    try {
      parameters.silent().forAll(checker, predicate);
      throw new AssertionError("Can't falsify " + getName());
    }
    catch (PropertyFalsified e) {
      return e;
    }
  }

  protected <T> T checkGeneratesExample(Generator<T> generator, Predicate<T> predicate, int minimizationSteps) {
    return checkFalsified(generator, predicate.negate(), minimizationSteps).getMinimalCounterexample().getExampleValue();
  }

  protected <T> PropertyFailure<T> checkFalsified(Generator<T> generator, Predicate<T> predicate, int minimizationSteps) {
    PropertyFalsified e = checkFails(STABLE, generator, predicate);
    //noinspection unchecked
    PropertyFailure<T> failure = (PropertyFailure<T>)e.getFailure();

    if (failure.getStoppingReason() != null) {
      throw new RuntimeException(failure.getStoppingReason());
    }

    /*
    System.out.println(" " + getName());
    System.out.println("Value: " + e.getBreakingValue());
    System.out.println("Data: " + e.getData());
    */
    assertEquals(minimizationSteps, failure.getTotalMinimizationExampleCount()); // to track that framework changes don't increase shrinking time significantly on average
    assertEquals(e.getBreakingValue(), generator.getGeneratorFunction().apply(((CounterExampleImpl)failure.getMinimalCounterexample()).createReplayData()));
    
    String strData = failure.getMinimalCounterexample().getSerializedData();
    //noinspection deprecation
    assertNotNull(checkFails(PropertyChecker.customized().rechecking(strData), generator, predicate));

    return failure;
  }

}
