package ppp;

public class Client {
  public void foo(Task task) {
    try {
      task.execute();
    }
    catch (RuntimeException e) {
    }
  }
}
