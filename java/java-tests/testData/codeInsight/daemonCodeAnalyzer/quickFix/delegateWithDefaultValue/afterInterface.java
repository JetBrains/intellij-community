// "Generate overloaded method with default parameter values" "true"
interface Test {
    default void foo() {
      foo();
  }

    void foo(int ii);
}