import java.util.HashMap;

public class Class2 extends HashMap {
  void foo() {
    new Runnable() {
      public void run() {
        Class2.super.put(<caret>)
      }
    };
  }
}