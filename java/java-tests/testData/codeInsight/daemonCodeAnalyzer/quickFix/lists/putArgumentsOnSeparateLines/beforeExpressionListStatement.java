// "Put arguments on separate lines" "false"

class A {
  void foo() {
    int size = 0;
    for (int i = 0; i < size; i++, i++<caret>, i++) {

    }
  }
}