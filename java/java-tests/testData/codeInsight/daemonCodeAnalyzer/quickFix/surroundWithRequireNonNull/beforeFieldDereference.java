// "Replace with 'Objects.requireNonNull(getObject())'" "true"

class MyClass {
  int a;

  static MyClass getObject() {
    return Math.random() > 0.5 ? new MyClass() : null;
  }
  void test() {
    getObject<caret>().a = 5;
  }
}