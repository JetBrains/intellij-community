// "Convert to atomic" "true-preview"
class Test {
  {
    int a = 42;
    int <caret>i = -a;
  }
}