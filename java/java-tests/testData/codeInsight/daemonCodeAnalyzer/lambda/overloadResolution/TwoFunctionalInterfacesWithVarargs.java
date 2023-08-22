import java.util.stream.Stream;

class Order {
  interface I { }
  interface I2 { }

  class Holder {
    public void registerProblem(I i1,
                                String s,
                                I2 ... i2s) { }

    public void registerProblem(Foo problemDescriptor) { }
  }
  class Foo {
    void f(Stream<Foo> stream, Holder holder) {
      stream.forEach(holder::registerProblem);
    }
  }

}
interface I {
  void foo(String s, Object... params);
}

class Foo {
  void n(I i) {}
  void n(Runnable r) {}
  void fooBar(String s, Object... params) {}
  {
    n(this::fooBar);
  }
}


class Foo1 {
  void n(I i) {}
  void n(Runnable r) {}
  static void fooBar(String s, Object... params) {}
  {
    n(Foo1::fooBar);
  }
}


interface I2 {
  void foo(Foo2 s, Object... params);
}

class Foo2 {
  void n(I2 i) {}
  void n(Runnable r) {}
  void fooBar(Object... params) {}
  {
    n(Foo2::fooBar);
  }

  void fooBar1(Object o, Object... params) {}
  {
    n(Foo2::fooBar1);
  }
}


class Foo3 {
  interface I3 {
    void foo(Foo3 s, Object... params);
  }

  interface I4 {
    void foo(Object params);
  }

  void n(I3 i) {}
  void n(I4 r) {}
  void fooBar(Object o, Object... params) {}
  {
    n(Foo3::fooBar);
  }
}