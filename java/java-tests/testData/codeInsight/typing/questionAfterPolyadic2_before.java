public class Foo {
  void test(int x, String s) {
    System.out.println(x+!s.trim().substring(new int[] {1,2,3}.length).isEmpty()<caret>);
  }
}