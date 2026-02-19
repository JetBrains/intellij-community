class A {
  public static void main(String[] args) {
    D d = new D();
    d.xyzzy();

    C c = new C();
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
class D extends B {
  public void xyzzy() {
  }
}
