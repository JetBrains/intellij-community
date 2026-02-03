class Test {
    int fieldName;

    void foo(int fieldName) {
        this.fieldName = fieldName;
    }
}

class TestImpl extends Test {
  void foo(int fieldName) {
    this.fieldName = fieldName;
  }
}