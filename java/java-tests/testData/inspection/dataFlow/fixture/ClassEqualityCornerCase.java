class Base {
  public void bar() {

  }
}

class Foo {
  public static void foo(Object a, Object b) {
    if (!(a instanceof Base) && !(b instanceof Base)) {
      return;
    }
    if (a instanceof Base) {}
    if (b instanceof Base) {}

    if (!a.getClass().equals(b.getClass())) {
      return;
    }

    if (<warning descr="Condition 'a instanceof Base' is always 'true'">a instanceof Base</warning>) {}
    if (<warning descr="Condition 'b instanceof Base' is always 'true'">b instanceof Base</warning>) {}
  }

  void test(Object a, Object b) {
    if (!(a instanceof String)) return;
    if (!a.getClass().equals(b.getClass())) {
      if (<warning descr="Condition 'b instanceof String' is always 'false'">b instanceof String</warning>) {}
    }
  }
}