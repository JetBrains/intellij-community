// "Fix all 'Constant conditions & exceptions' problems" "true"
public class Test {
  void foo1() {
    int k = 0;
    int i = 0;
    if (i == k) {}
    if (i == k) {}
    if (i == k) {}
    if (i == k) {}
    if (i == k) {}
    if (i == k) {}
  }

  void foo2() {
    int k = 0;
    int i = 0;
    if (<caret>i == k) {}
    if (i == k) {}
    if (i == k) {}
    if (i == k) {}
    if (i == k) {}
    if (i == k) {}
  }
}