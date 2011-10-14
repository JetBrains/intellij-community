class B1 {
  public B1(int i, String... s) {}
}
class A1 extends B1 {
  A1() {
    super(1, <selection>"a", "b"</selection>);
  }
}