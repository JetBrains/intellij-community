// "Generate overloaded method with default parameter value" "true"
interface Test {
    static void foo() {
      foo();
  }

    static void foo(int ii) {}
}