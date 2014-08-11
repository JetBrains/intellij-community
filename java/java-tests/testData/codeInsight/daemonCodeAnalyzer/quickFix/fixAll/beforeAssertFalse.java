// "Fix all 'Constant conditions & exceptions' problems in file" "true"
public class Test {
  void foo2() {
    int k = 0;
    int i = 0;
    assert i <caret>!= k;
  }
}