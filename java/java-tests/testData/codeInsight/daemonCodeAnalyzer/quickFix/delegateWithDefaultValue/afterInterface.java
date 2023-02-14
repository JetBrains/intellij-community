// "Generate overloaded method with default parameter values" "true"
interface Test {
    default void foo() {
      foo(0);
  }

    void foo(int ii);
}