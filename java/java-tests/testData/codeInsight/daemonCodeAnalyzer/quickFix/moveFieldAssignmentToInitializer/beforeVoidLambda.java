// "Move assignment to field declaration" "true-preview"
public class Main {
  int i;

  public void test() {
    Runnable r = () -> i <caret>= 1;
  }
}