abstract class A {
  public abstract D foo();
}

interface B {
  F foo();
}

class C extends A implements B {
    public F foo() {
        <selection>return null;  //To change body of implemented methods use File | Settings | File Templates.</selection>
    }
}

class D {}
class F extends D {}