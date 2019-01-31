// "Join declaration and assignment" "false"
class T {
  static int foo() {
    int x, y;
    <caret>x = y = 0;
    return x + y;
  }
}