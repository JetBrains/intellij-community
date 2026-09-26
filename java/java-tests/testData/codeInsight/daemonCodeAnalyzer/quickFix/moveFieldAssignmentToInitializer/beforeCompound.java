// "Move assignment to field declaration" "false"
public class Main {
  int i;

  Main() {
    i += 1;
  }

  public void test() {
    i +<caret>= 1;
  }
}