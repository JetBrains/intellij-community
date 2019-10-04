// "Move assignment to field declaration" "false"
public class Main {
  int i;

  public int test(int x) {
    return switch (x) {
      case 1 -> i <caret>= 1;
    };
  }
}