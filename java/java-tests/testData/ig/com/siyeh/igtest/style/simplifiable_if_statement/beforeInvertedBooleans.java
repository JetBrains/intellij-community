// "Replace 'if else' with '=='" "GENERIC_ERROR_OR_WARNING"
public class Test {
  void test(boolean a, boolean b) {
    boolean c;
    <caret>if (a) {
      c = b;
    } else {
      c = !b;
    }
  } 
}