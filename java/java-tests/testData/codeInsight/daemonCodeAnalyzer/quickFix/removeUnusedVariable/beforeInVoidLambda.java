// "Remove field 'x'" "true-preview"
public class Main {
  private int <caret>x;

  void test() {
    Runnable r = () -> x = 1;
  }
}