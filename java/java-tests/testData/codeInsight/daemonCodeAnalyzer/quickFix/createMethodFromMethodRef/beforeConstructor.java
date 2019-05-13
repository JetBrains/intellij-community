// "Create constructor" "true"
class FooBar {
  FooBar(int i) {
  }

  {
    Runnable r = FooBar::ne<caret>w;
  }
}
