
import java.io.IOException;

class Test {

  interface ThrowableRunnable<T extends Throwable> {
    void run() throws T;
  }

  interface ThrowableComputable<R, E extends Throwable> {
    R compute() throws E;
  }

  private <D extends Throwable> void doTest(ThrowableRunnable<D> action) { }


  public void testNoNotificationForWorkspace() {
    doTest(() -> computeAndWait(() -> foo()));
  }

  private String foo() throws IOException {
    return null;
  }


  public static <R, E extends Throwable> R computeAndWait(ThrowableComputable<R, E> action) throws E {
    return null;
  }
}

class TestWithImplicitLambda {

  interface ThrowableRunnable<T extends Throwable> {
    void run(int k) throws T;
  }

  interface ThrowableComputable<R, E extends Throwable> {
    R compute() throws E;
  }

  private <D extends Throwable> void doTest(ThrowableRunnable<D> action) { }


  public void testNoNotificationForWorkspace() {
    doTest((k) -> computeAndWait(() -> foo()));
  }

  private String foo() throws IOException {
    return null;
  }


  public static <R, E extends Throwable> R computeAndWait(ThrowableComputable<R, E> action) throws E {
    return null;
  }
}