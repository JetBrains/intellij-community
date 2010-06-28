<error descr="Modifier 'abstract' not allowed here">abstract</error> enum OurEnum {
  A <error descr="Class 'Anonymous class derived from OurEnum' must either be declared abstract or implement abstract method 'foo()' in 'OurEnum'">{</error>
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
  Y <error descr="Class 'Anonymous class derived from xxx' must either be declared abstract or implement abstract method 'f()' in 'xxx'">{</error>
  };

  abstract void f();
}

enum ok {
  X { void f() {} };
  abstract void f();
}
