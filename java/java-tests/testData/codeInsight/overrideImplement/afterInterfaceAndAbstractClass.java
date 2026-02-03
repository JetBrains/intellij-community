abstract class A {
  public abstract D foo();
}

interface B {
  F foo();
}

class C extends A implements B {
    public F foo() {
        <selection>return null;</selection>
    }
}

class D {}
class F extends D {}