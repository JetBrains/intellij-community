class Test1 {
  interface A {
    default void foo() { }
  }

  abstract class B {
    public abstract void foo();
  }

  abstract class C extends B implements A { }
}

class Test2 {
  interface A {
    default void foo() { }
  }

  interface B extends A {}

  interface C extends A {
    default void foo() {}
  }
  abstract  class D implements  C, B { }
}


class Test3 {

  interface A {
    void accept();
  }
  interface B extends A {}

  interface C extends A {
    default void accept(){}
  }

  class D implements B, C {}
}

class Test4 {

  interface A {
    default void accept(){}
  }
  interface B extends A {}

  interface C extends A {
    void accept();
  }

  <error descr="Class 'D' must either be declared abstract or implement abstract method 'accept()' in 'C'">class <error descr="Class 'D' must either be declared abstract or implement abstract method 'accept()' in 'C'">D</error> implements B, C</error> {}
  abstract class E implements B, C {}
}

