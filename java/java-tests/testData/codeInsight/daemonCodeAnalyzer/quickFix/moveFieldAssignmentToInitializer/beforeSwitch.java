// "Move assignment to field declaration" "true-preview"
public class Main {
  int i;

  public void test(int x) {
    switch (x) {
      case 1 -> i <caret>= 1;
    }
  }
}