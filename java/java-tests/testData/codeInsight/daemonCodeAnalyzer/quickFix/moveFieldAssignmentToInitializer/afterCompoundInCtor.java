// "Move assignment to field declaration" "true"
public class Main {
  int i = 1;

  Main() {
    i += 1;
  }

  public void test() {
    i = 1;
  }
}