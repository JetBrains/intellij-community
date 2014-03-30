public class Foo {
  private void f() {
    int x = 10;
    int t = x;
    x += 10;
    System.out.println(t);
  }
}