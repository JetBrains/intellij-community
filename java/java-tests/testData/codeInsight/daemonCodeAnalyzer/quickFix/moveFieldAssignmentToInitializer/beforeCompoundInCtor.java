// "Move assignment to field declaration" "true-preview"
public class Main {
  int i;

  Main() {
    i += 1;
  }

  public void test() {
    i <caret>= 1;
  }
}