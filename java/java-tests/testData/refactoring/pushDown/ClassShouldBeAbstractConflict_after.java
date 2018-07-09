abstract class A {
  abstract void foo();
}

class B extends A {
}

class C extends B {
    @Override
    public void foo() { }
}