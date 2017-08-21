package jetCheck;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author peter
 */
@SuppressWarnings("ExceptionClassNameDoesntEndWithException")
public class PropertyFalsified extends RuntimeException {
  static final String FAILURE_REASON_HAS_CHANGED_DURING_MINIMIZATION = "Failure reason has changed during minimization, see initial failing example below";
  private static final String SEPARATOR = "\n==========================\n";
  private final PropertyFailureImpl<?> failure;
  private final Supplier<DataStructure> data;
  private final String message;

  PropertyFalsified(PropertyFailureImpl<?> failure, Supplier<DataStructure> data) {
    super(failure.getMinimalCounterexample().getExceptionCause());
    this.failure = failure;
    this.data = data;
    this.message = calcMessage();
  }

  @Override
  public String getMessage() {
    return message;
  }

  private String calcMessage() {
    StringBuilder traceBuilder = new StringBuilder();
    
    String msg = "Falsified on " + valueToString(failure.getMinimalCounterexample(), traceBuilder) + "\n" +
                 getMinimizationStats() +
                 failure.iteration.printToReproduce();

    Throwable failureReason = failure.getMinimalCounterexample().getExceptionCause();
    if (failureReason != null) {
      Throwable rootCause = getRootCause(failureReason);
      appendTrace(traceBuilder, 
                  rootCause == failureReason ? "Property failure reason: " : "Property failure reason, innermost exception (see full trace below): ", 
                  rootCause);
    }

    if (failure.getStoppingReason() != null) {
      msg += "\n Minimization stopped prematurely, see the reason below.";
      appendTrace(traceBuilder, "An unexpected exception happened during minimization: ", failure.getStoppingReason());
    }
    
    Throwable first = failure.getFirstCounterExample().getExceptionCause();
    if (exceptionsDiffer(first, failure.getMinimalCounterexample().getExceptionCause())) {
      msg += "\n " + FAILURE_REASON_HAS_CHANGED_DURING_MINIMIZATION;
      StringBuilder secondaryTrace = new StringBuilder();
      traceBuilder.append("\n Initial value: ").append(valueToString(failure.getFirstCounterExample(), secondaryTrace));
      if (first == null) {
        traceBuilder.append("\n Initially property was falsified without exceptions");
        traceBuilder.append(secondaryTrace);
      } else {
        traceBuilder.append(secondaryTrace);
        appendTrace(traceBuilder, "Initially failed because of ", first);
      }
    }
    return msg + traceBuilder;
  }

  private static Throwable getRootCause(Throwable t) {
    while (t.getCause() != null) {
      t = t.getCause();
    }
    return t;
  }

  private static void appendTrace(StringBuilder traceBuilder, String prefix, Throwable e) {
    traceBuilder.append("\n ").append(prefix).append(StatusNotifier.printStackTrace(e)).append(SEPARATOR);
  }

  private static String valueToString(CounterExampleImpl<?> example, StringBuilder traceBuilder) {
    try {
      return String.valueOf(example.getExampleValue());
    }
    catch (Throwable e) {
      appendTrace(traceBuilder, "Exception during toString evaluation: ", e);
      return "<can't evaluate toString(), see exception below>";
    }
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
