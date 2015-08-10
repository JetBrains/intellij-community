// "Generate overloaded method with default parameter value" "true"
interface Test {
    default void foo() {
      foo();
  }

    void foo(int ii);
}