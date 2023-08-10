<error descr="Modifier 'abstract' not allowed here">abstract</error> enum OurEnum {
  <error descr="Enum constant 'A' must implement abstract method 'foo()' in 'OurEnum'">A</error> {
  },
  <error descr="Enum constant 'B' must implement abstract method 'foo()' in 'OurEnum'">B</error>,
  C {
    void foo() {}
  }
  ;

  abstract void foo();
}

enum xxx {
  <error descr="Enum constant 'X' must implement abstract method 'f()' in 'xxx'">X</error>,
  <error descr="Enum constant 'Y' must implement abstract method 'f()' in 'xxx'">Y</error> {
  };

  abstract void f();
}

enum ok {
  X { void f() {} };
  abstract void f();
}
