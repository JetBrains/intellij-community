public class Test {
  void run() {
    MyBox<Void> box = new MyBox<>(null)<caret>
  }

  record MyBox<T>(T t) {}
}
