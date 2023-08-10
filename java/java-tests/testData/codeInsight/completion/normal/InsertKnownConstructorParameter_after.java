public class Test {
  void run() {
    MyBox<Class<String>> box = new MyBox<>(String.class)<caret>
  }

  record MyBox<T>(T t) {}
}
