import java.util.function.Supplier;

class Test {
  static void submit(String name, Runnable runnable) {}
  static <T> void submit(String name, Supplier<? extends T> supplier) {}

  static class Holder<T> {
    Holder(T value) {}
  }

  static class Pair<A, B> {
    Pair(A first, B second) {}
  }

  void test(boolean flag) {
    <caret>submit("name", () -> {
      if (flag) {
        return null;
      }
      var holder = new Holder<>("value");
      return new Pair<>(holder, "second");
    });
  }
}