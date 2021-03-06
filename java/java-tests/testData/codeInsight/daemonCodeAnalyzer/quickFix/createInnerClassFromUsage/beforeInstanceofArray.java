// "Create inner class 'Foo'" "false"
public class Test {
  boolean foo(int[] o) {
    return o instanceof F<caret>oo;
  }
}