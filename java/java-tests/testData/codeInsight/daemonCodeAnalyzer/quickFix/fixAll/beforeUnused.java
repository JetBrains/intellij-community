// "Fix all 'Unused declaration' problems in file" "false"
public class Test {
  void f<caret>oo1() {
    int k = 0;
    int i = 0;
    if (i == k) {}
    if (i == k) {}
    if (i == k) {}
    if (i == k) {}
    if (i == k) {}
    if (i == k) {}
  }
}