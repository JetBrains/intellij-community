// "Remove unnecessary 'this' qualifier" "true-preview"
class Main {
  int x = 42;
  void test() {
    int y = Main.thi<caret>s.x + 12;
  }
}