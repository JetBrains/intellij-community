class A {
  public static void main(String[] args) {
    D d = new D();
    d.xyzzy();

    CChild c = new CChild();
    c.xyzzy();
  }
}
class B {
  public void xyzzy() {
  }
}
class C extends B {
  public void xyzzy() {
  }
}
class CChild extends C {
}
class D extends B {
  public void xyzzy() {
  }
}
