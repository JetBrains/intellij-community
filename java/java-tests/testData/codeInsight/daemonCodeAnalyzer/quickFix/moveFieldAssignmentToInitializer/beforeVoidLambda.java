// "Move assignment to field declaration" "true"
public class Main {
  int i;

  public void test() {
    Runnable r = () -> i <caret>= 1;
  }
}