// "Remove field 'x'" "true"
public class Main {
  private int <caret>x;

  void test() {
    Runnable r = () -> x = 1;
  }
}