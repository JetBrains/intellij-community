// "Replace with 'Math.max()' call" "true"
class Test {

  class A {
    int field;

    int getField() {
      return field;
    }
  }

  int getMax(A a, A b) {
    if<caret> (a.getField() > b.getField()) {
      return a.getField();
    }
    else {
      return b.getField();
    }
  }
}