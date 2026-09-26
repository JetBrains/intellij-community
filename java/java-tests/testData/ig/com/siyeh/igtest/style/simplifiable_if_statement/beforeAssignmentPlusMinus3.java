// "Replace 'if else' with '?:'" "true"
class PlusMinusTest {
  void foo(int a, int b) {
    int c = 0;
    if<caret> (a < b) { c += a - b; } else { c -= a - b; }
  }
}