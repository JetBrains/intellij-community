// "Apply all 'Make not abstract' fixes in file" "true"
public interface InterfaceToClass {
  void foo() {

  }

  static void bar() {
    class Qux {
      <caret>abstract static void qux() {}
      abstract static void qux1() {}
      abstract static void qux2() {}
      abstract static void qux3() {}
    }
  }

  void baz() {
  }
}
