interface Z {
  void m();
}

interface X extends Z {}
interface Y extends Z {}

class Test {
  {
    ((X & <caret>Y) () -> {}).m();
  }
}