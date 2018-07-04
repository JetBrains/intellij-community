/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jetCheck;

import static org.jetbrains.jetCheck.Generator.*;

/**
 * @author peter
 */
public class ExceptionTest extends PropertyCheckerTestCase {

  public void testFailureReasonUnchanged() {
    PropertyFalsified e = checkFails(STABLE, integers(), i -> {
      throw new AssertionError("fail");
    });

    assertFalse(e.getMessage().contains(PropertyFalsified.FAILURE_REASON_HAS_CHANGED_DURING_MINIMIZATION));
  }

  public void testFailureReasonChangedExceptionClass() {
    PropertyFalsified e = checkFails(STABLE, integers(), i -> {
      throw (i == 0 ? new RuntimeException("fail") : new IllegalArgumentException("fail"));
    });
    assertTrue(e.getMessage().contains(PropertyFalsified.FAILURE_REASON_HAS_CHANGED_DURING_MINIMIZATION));
  }

  public void testFailureReasonChangedExceptionTrace() {
    PropertyFalsified e = checkFails(STABLE, integers(), i -> {
      if (i == 0) {
        throw new AssertionError("fail");
      }
      else {
        throw new AssertionError("fail2");
      }
    });
    assertTrue(e.getMessage().contains(PropertyFalsified.FAILURE_REASON_HAS_CHANGED_DURING_MINIMIZATION));
  }

  public void testExceptionWhileGeneratingValue() {
    try {
      STABLE.forAll(from(data -> {
        throw new AssertionError("fail");
      }), i -> true);
      fail();
    }
    catch (GeneratorException ignore) {
    }
  }

  public void testExceptionWhileShrinkingValue() {
    PropertyFalsified e = checkFails(PropertyChecker.customized(), listsOf(integers()).suchThat(l -> {
      if (l.size() == 1 && l.get(0) == 0) throw new RuntimeException("my exception");
      return true;
    }), l -> l.stream().allMatch(i -> i > 0));

    assertEquals("my exception", e.getFailure().getStoppingReason().getMessage());
    assertTrue(StatusNotifier.printStackTrace(e).contains("my exception"));
  }

  public void testUnsatisfiableSuchThat() {
    try {
      PropertyChecker.forAll(integers(-1, 1).suchThat(i -> i > 2), i -> i == 0);
      fail();
    }
    catch (GeneratorException e) {
      assertTrue(e.getCause() instanceof CannotSatisfyCondition);
    }
  }

  public void testUsingWrongDataStructure() {
    Generator<Integer> gen = from(data1 -> {
      int i1 = data1.generate(naturals());
      int i2 = data1.generate(from(data2 -> data1.generate(integers())));
      return i1 + i2;
    });
    try {
      PropertyChecker.forAll(gen, i -> true);
      fail();
    }
    catch (WrongDataStructure expected) {
    }
  }
}
