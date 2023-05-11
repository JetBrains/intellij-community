package ppp;

public class Client2 {
  public void foo(Task task) {
    try {
      task.execute2();
    }
    catch (Throwable/*RuntimeException*/ e) {
    }
  }
}
