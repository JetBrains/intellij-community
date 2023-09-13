// "Replace with unnamed pattern" "true-preview"
public class Test {
  record R(int x, int y) {}
  void test(Object obj) {
    switch(obj) {
      case R(_, int b) {}
    }
  }
}