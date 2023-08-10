// "Replace with unnamed pattern" "false"
public class Test {
  record R(Object cmp) {}
  void test(Object obj) {
    if (obj instanceof R(String <caret>a)) {

    }
  }
}