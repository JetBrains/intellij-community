class Foo {
  private void f() {
    int x = 10;
    int t = x;
    <warning descr="The value 10 assigned to 'x' is never used">x</warning> += 10;
    System.out.println(t);
  }
}