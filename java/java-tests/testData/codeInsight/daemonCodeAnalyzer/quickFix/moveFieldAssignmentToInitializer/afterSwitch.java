// "Move assignment to field declaration" "true-preview"
public class Main {
  int i = 1;

  public void test(int x) {
    switch (x) {
      case 1 -> {
      }
    }
  }
}