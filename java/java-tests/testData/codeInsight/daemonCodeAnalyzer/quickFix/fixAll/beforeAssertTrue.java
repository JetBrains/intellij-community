// "Fix all 'Constant values' problems in file" "true"
public class Test {
  void foo2() {
    int k = 0;
    int i = 0;
    assert i <caret>== k;
    assert i == k;
    if (i == k) {
      System.out.println();
    }
  }
}