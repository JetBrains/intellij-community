import java.util.concurrent.atomic.AtomicReference;

class Test {
  AtomicReference<String> s;

  void foo() {
    String s1 = s.get().length() > 2 ? s.get().substring(1) : s.get().trim();
  }
}