abstract class A {
  public abstract D foo();
}

interface B {
  F foo();
}

class C extends A implements B {
    <caret>
}

class D {}
class F extends D {}