public class Test {
  void run() {
    MyBox<Class<String>> box = new MyB<caret>
  }

  record MyBox<T>(T t) {}
}
