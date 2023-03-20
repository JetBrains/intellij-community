public class Test {
  void run() {
    MyBox<Void> box = new MyB<caret>
  }

  record MyBox<T>(T t) {}
}
