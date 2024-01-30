package p2;

class A {
  public void foo(){
    System.out.println("");
  }
}

class B extends A {
  @Override
  public void foo() {}
}

class C extends B {
  @Override
  public void foo() {}
}
