// "Apply all 'Make default' fixes in file" "true"
public interface InterfaceToClass {
  <caret>void foo() {

  }

  static void bar() {
    class Qux {
      abstract static void qux() {}
      abstract static void qux1() {}
      abstract static void qux2() {}
      abstract static void qux3() {}
    }
  }

  void baz() {
  }
}
