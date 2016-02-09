// "Replace with lambda" "true"
class A {
  {
    bar(() -> foo());
  }

  private <T> void bar(ThrowableComputable<T, Exception> throwableComputable) {}

  private <K> K foo() throws Exception {
    return null;
  }

  interface ThrowableComputable<T, T1 extends Throwable> {
    T compute() throws T1;
  }
}