<error descr="Modifier 'abstract' not allowed here">abstract</error> enum OurEnum {
  <error descr="Class 'Anonymous class derived from OurEnum' must implement abstract method 'foo()' in 'OurEnum'">A</error> {
  },
  <error descr="'OurEnum' is abstract; cannot be instantiated">B</error>,
  C {
    void foo() {}
  }
  ;

  abstract void foo();
}

enum xxx {
  <error descr="'xxx' is abstract; cannot be instantiated">X</error>,
  <error descr="Class 'Anonymous class derived from xxx' must implement abstract method 'f()' in 'xxx'">Y</error> {
  };

  abstract void f();
}

enum ok {
  X { void f() {} };
  abstract void f();
}
