package java.lang;
public interface StringTemplate {
  Processor<String, RuntimeException> STR = null;

  @PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
  @FunctionalInterface
  public interface Processor<R, E extends Throwable> {
    R process(StringTemplate stringTemplate) throws E;
  }
}
class Main {

  class FailureException extends Exception {}

  public static void x(StringTemplate.Processor<String, FailureException> p, int i, int j) {
    try {
      String t = newMethod(p, i, j);
    }
    catch (FailureException ignore) {}
  }

    private static String newMethod(StringTemplate.Processor<String, FailureException> p, int i, int j) throws FailureException {
        return p."\{i} + \{j} = \{i + j}";
    }
}