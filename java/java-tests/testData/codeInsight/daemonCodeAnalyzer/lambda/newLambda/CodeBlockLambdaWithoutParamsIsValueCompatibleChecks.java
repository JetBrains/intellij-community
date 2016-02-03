class Test {
  {
    bar(new ThrowableComputable<String, Exception>() {
      @Override
      public String compute() throws Exception {
        return foo();
      }
    });
  }

  private <T> void bar(ThrowableComputable<T, Exception> throwableComputable) {}

  private <K> K foo() throws Exception {
    return null;
  }

  interface ThrowableComputable<T, T1 extends Throwable> {
    T compute() throws T1;
  }
}