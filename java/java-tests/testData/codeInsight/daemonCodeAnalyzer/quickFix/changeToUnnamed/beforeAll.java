// "Apply all 'Replace with unnamed pattern' fixes in file" "true"
public class Test {
  record R(int x, int y) {}
  void test(Object obj) {
    if (obj instanceof R(int <caret>a, int b)) {

    }
  }
}