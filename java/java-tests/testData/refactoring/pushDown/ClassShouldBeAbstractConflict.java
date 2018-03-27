abstract class A {
  abstract void foo();
}

class B extends A {
  @Override
  public void fo<caret>o() { }
}

class C extends B {
}