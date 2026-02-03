// "Convert to atomic" "true"
class Test {
  int <caret>o;
  int j = o;

  void foo() {
    while ((o = j) != 0) {}
  }
}