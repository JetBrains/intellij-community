// "Convert to atomic" "true"
class Test {
  {
    int a = 42;
    int <caret>i = -a;
  }
}