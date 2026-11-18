class MultiCatchParametersComparison {
  static void invoke(Runnable work, Runnable cleanupAction, String s) {
    try {
      work.run();
    }
    catch (IllegalArgumentException | Error e) {
      try {
        cleanupAction.run();
      }
      catch (RuntimeException | Error cleanup) {
        if (cleanup != e) {
          e.addSuppressed(cleanup);
        }
        boolean same = cleanup == e;
        boolean reversed = e == cleanup;
        boolean incompatible = <error descr="Operator '==' cannot be applied to 'java.lang.RuntimeException | java.lang.Error', 'java.lang.String'">cleanup == s</error>;
        Error error = (Error)cleanup;
      }
      throw e;
    }
  }
}
