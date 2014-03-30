class A {
  protected int i;
}

class B extends A {}

class C {
  C() {
    B d = new D(); 
  }
  
  static class <caret>D extends B {
    D() {
      System.out.println(i);
    }
  }
}