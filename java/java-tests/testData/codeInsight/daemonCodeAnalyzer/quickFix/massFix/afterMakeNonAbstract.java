// "Apply all 'Make not abstract' fixes in file" "true"
public interface InterfaceToClass {
  void foo() {

  }

  static void bar() {
    class Qux {
      static void qux() {}
      static void qux1() {}
      static void qux2() {}
      static void qux3() {}
    }
  }

  void baz() {
  }
}
