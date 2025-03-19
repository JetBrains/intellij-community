// "Replace 'if else' with '?:'" "true"
class PlusMinusTest {
  void foo(boolean b) {
    int x = 0;
      x += b ? 1 : -1;
  }
}