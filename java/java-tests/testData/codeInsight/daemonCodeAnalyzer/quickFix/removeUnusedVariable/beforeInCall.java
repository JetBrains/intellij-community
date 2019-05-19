// "Remove variable 'i'" "true"
public class Main {
  void test(String s) {
    int <caret>i;
    foo(i = 1);
  }
}