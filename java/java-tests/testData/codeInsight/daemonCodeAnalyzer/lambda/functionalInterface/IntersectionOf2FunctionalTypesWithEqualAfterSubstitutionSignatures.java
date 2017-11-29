interface X  {
  void m(Integer i);
}

interface Y<T> {
  void m(T t);
}

class Test {
  {
    ((X & <caret>Y<Integer>) (x) -> {}).m(1);
  }
}