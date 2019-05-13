interface X  {
  void m();
}

interface Y {
  void m();
}

class Test {
  {
    ((X & <caret>Y) () -> {}).m();
  }
}