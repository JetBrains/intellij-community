// "Generate overloaded method with default parameter value" "true"
abstract class Test {
    int foo(boolean... args) {
      return foo(<caret>, args);
  }

    abstract int foo(int ii, boolean... args);
}