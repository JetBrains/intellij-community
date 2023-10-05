class Main {

  class FailureException extends Exception {}

  public static void x(StringTemplate.Processor<String, FailureException> p, int i, int j) {
    try {
      String t = <selection>p."\{i} + \{j} = \{i + j}";</selection>
    }
    catch (FailureException ignore) {}
  }
}