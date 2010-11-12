class Test {
  void foo(Object o) {
    <selection>
    if (o instanceof A1) {
      ((A1)o).doSmth();
    }
    </selection>
  }
}

class A1 {}