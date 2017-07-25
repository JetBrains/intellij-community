package jetCheck;

import static jetCheck.Generator.*;

/**
 * @author peter
 */
public class ExceptionTest extends PropertyCheckerTestCase {

  public void testFailureReasonUnchanged() {
    PropertyFalsified e = checkFails(forAllStable(integers()), i -> {
      throw new AssertionError("fail");
    });

    assertFalse(e.getMessage().contains(PropertyFalsified.FAILURE_REASON_HAS_CHANGED_DURING_MINIMIZATION));
  }

  public void testFailureReasonChangedExceptionClass() {
    PropertyFalsified e = checkFails(forAllStable(integers()), i -> {
      throw (i == 0 ? new RuntimeException("fail") : new IllegalArgumentException("fail"));
    });
    assertTrue(e.getMessage().contains(PropertyFalsified.FAILURE_REASON_HAS_CHANGED_DURING_MINIMIZATION));
  }

  public void testFailureReasonChangedExceptionTrace() {
    PropertyFalsified e = checkFails(forAllStable(integers()), i -> {
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
      forAllStable(from(data -> {
        throw new AssertionError("fail");
      })).shouldHold(i -> true);
      fail();
    }
    catch (GeneratorException ignore) {
    }
  }

  public void testExceptionWhileShrinkingValue() {
    PropertyFalsified e = checkFails(PropertyChecker.forAll(listsOf(integers()).suchThat(l -> {
      if (l.size() == 1 && l.get(0) == 0) throw new RuntimeException("my exception");
      return true;
    })), l -> l.stream().allMatch(i -> i > 0));

    assertEquals("my exception", e.getFailure().getStoppingReason().getMessage());
    assertTrue(StatusNotifier.printStackTrace(e).contains("my exception"));
  }

  public void testUnsatisfiableSuchThat() {
    try {
      PropertyChecker.forAll(integers(-1, 1).suchThat(i -> i > 2)).shouldHold(i -> i == 0);
      fail();
    }
    catch (GeneratorException e) {
      assertTrue(e.getCause() instanceof CannotSatisfyCondition);
    }
  }

}
