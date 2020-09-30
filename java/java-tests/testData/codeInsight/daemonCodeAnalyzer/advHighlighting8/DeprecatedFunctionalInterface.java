@Deprecated
interface I {
  void m();
}

class Util {
  @Deprecated
  static void foo(I i) {}
}
class MyTest {
  {
    Util.<warning descr="'foo(I)' is deprecated">foo</warning>(() -> {});
  }
}