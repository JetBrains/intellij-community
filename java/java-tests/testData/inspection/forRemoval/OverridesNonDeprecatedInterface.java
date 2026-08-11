interface A {
  void foo();
}

@Deprecated(forRemoval = true)
class B implements A {
  @Override
  public void foo() {
  }

  public void bar() {
  }
}

class C extends <error descr="'B' is deprecated and marked for removal">B</error> {
}

class Usages {
  void test(C c) {
    // 'foo' is part of the non-deprecated contract of interface 'A', so the call must not be reported
    c.foo();
    // 'bar' is declared only in the marked-for-removal class 'B', so the call is still reported
    c.<error descr="'B' is deprecated and marked for removal">bar</error>();
  }
}

interface D {
  void baz();
}

class E implements D {
  @Override
  @Deprecated(forRemoval = true)
  public void baz() {
  }
}

class ExplicitOverrideUsage {
  void test(E e) {
    // 'baz' is explicitly marked for removal on the override itself, so the call is still reported
    e.<error descr="'baz()' is deprecated and marked for removal">baz</error>();
  }
}

class G {
  public int value;
}

@Deprecated(forRemoval = true)
class H extends G {
  public int value;
}

class I extends <error descr="'H' is deprecated and marked for removal">H</error> {
}

class FieldHidingUsage {
  int test(I i) {
    // 'value' resolves to the field hidden in marked-for-removal 'H' (distinct from G.value), so it is still reported
    return i.<error descr="'H' is deprecated and marked for removal">value</error>;
  }
}
