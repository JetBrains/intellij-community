public class NarrowFilter {
  void test(int <flown11>x) {
    if (x > 0) {
      foo(<flown1>x);
    } else {
      bar(x);
    }
  }

  void foo(int <caret>x) { }
  void bar(int x) { }

  void call() {
    test(<flown111>1);
    test(-1);
  }
}