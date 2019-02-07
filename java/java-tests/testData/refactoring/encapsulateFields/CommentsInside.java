public class A {
  public int i;

  void foo(A a) {
    System.out.println(a./*comment*/i);
    a/*comment*/.i = 42;
  }
}
