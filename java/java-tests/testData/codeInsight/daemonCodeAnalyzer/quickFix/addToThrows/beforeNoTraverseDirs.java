// "Add exception to method signature" "false"
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

class Freeze implements Runnable {

  private boolean flag;

  @Override
  public void run() {
    try (SomeClient client = new SomeClient()) {
      if (flag) {
        client.<caret>run1();
      } else {
        client.run2();
      }
    }
  }
}
class SomeClient implements AutoCloseable {

  public void run1() throws InterruptedException, ExecutionException, TimeoutException {}

  public void run2() throws InterruptedException, ExecutionException, TimeoutException {}

  @Override
  public void close() {}
}
