public class A {
  public int i;

  void foo(A a) {
    System.out.println(a./*comment*/getI());
    a/*comment*/.setI(42);
  }

    public int getI() {
        return i;
    }

    public void setI(int i) {
        this.i = i;
    }
}
