// "Generate overloaded method with default parameter values" "true"
interface Test {
    static void foo() {
      foo();
  }

    static void foo(int ii) {}
}