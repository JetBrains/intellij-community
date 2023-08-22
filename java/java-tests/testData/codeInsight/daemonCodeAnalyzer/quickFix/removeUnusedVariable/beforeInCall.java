// "Remove local variable 'i'" "true-preview"
public class Main {
  void test(String s) {
    int <caret>i;
    foo(i = 1);
  }
}