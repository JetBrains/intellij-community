// "Replace 'if else' with '=='" "GENERIC_ERROR_OR_WARNING"
public class Test {
  void test(int a, int b, int c) {
    boolean f;
    <caret>if (a > 0) {
      f = b < c;
    } else {
      f = b >= c;
    }
  } 
}