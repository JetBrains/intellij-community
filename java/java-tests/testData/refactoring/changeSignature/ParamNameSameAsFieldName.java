class Test {
    int fieldName;

    void fo<caret>o(int name) {
        fieldName = name;
    }
}

class TestImpl extends Test {
  void foo(int name) {
    fieldName = name;
  }
}