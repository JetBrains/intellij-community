package jetCheck;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author peter
 */
@SuppressWarnings("ExceptionClassNameDoesntEndWithException")
public class PropertyFalsified extends RuntimeException {
  static final String FAILURE_REASON_HAS_CHANGED_DURING_MINIMIZATION = "!!! FAILURE REASON HAS CHANGED DURING MINIMIZATION !!!";
  private static final String SEPARATOR = "\n==========================\n";
  private final PropertyFailureImpl<?> failure;
  private final Supplier<DataStructure> data;

  PropertyFalsified(PropertyFailureImpl<?> failure, Supplier<DataStructure> data) {
    super(failure.getMinimalCounterexample().getExceptionCause());
    this.failure = failure;
    this.data = data;
  }

  @Override
  public String getMessage() {
    String msg = "Falsified on " + failure.getMinimalCounterexample().getExampleValue() + "\n" +
                 getMinimizationStats() +
                 failure.iteration.printToReproduce();

    if (failure.getStoppingReason() != null) {
      msg += "\n Shrinking stopped because of " + StatusNotifier.printStackTrace(failure.getStoppingReason());
      msg += SEPARATOR;
    }
    
    Throwable first = failure.getFirstCounterExample().getExceptionCause();
    if (exceptionsDiffer(first, failure.getMinimalCounterexample().getExceptionCause())) {
      msg += "\n " + FAILURE_REASON_HAS_CHANGED_DURING_MINIMIZATION;
      msg += "\n Initial value: " + failure.getFirstCounterExample().getExampleValue() + "\n";
      if (first != null) {
        msg += "\n Initial exception: " + StatusNotifier.printStackTrace(first) + SEPARATOR;
      } else {
        msg += "\n Initially property was falsified without exceptions\n";
      }
    }
    return msg;
  }

  private String getMinimizationStats() {
    int exampleCount = failure.getTotalMinimizationExampleCount();
    if (exampleCount == 0) return "";
    String examples = exampleCount == 1 ? "example" : "examples";

    int stageCount = failure.getMinimizationStageCount();
    if (stageCount == 0) return "Couldn't minimize, tried " + exampleCount + " " + examples + "\n";

    String stages = stageCount == 1 ? "stage" : "stages";
    return "Minimized in " + stageCount + " " + stages + ", by trying " + exampleCount + " " + examples + "\n";
  }

  private static boolean exceptionsDiffer(Throwable e1, Throwable e2) {
    if (e1 == null && e2 == null) return false;
    if ((e1 == null) != (e2 == null)) return true;
    if (!e1.getClass().equals(e2.getClass())) return true;
    if (e1 instanceof StackOverflowError) return false;

    return !getUserTrace(e1).equals(getUserTrace(e2));
  }

  private static List<String> getUserTrace(Throwable e) {
    List<String> result = new ArrayList<>();
    for (StackTraceElement element : e.getStackTrace()) {
      String s = element.toString();
      if (s.startsWith("jetCheck.CounterExampleImpl.checkProperty")) {
        break;
      }
      result.add(s);
    }
    return result;
  }

  public PropertyFailure<?> getFailure() {
    return failure;
  }

  public Object getBreakingValue() {
    return failure.getMinimalCounterexample().getExampleValue();
  }

  public DataStructure getData() {
    return data.get();
  }
}
