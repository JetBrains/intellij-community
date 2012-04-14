// "Fix all 'Constant conditions & exceptions' problems" "true"
public class Test {
  void foo2() {
    int k = 0;
    int i = 0;
    assert <caret>i == k;
    assert i == k;
    if (i == k) {
      System.out.println();
    }
  }
}