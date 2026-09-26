// "Replace with 'Math.max()' call" "true"
class Test {

  class A {
    int field;

    int getField() {
      return field;
    }
  }

  int getMax(A a, A b) {
      return Math.max(a.getField(), b.getField());
  }
}