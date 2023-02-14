// "Generate overloaded method with default parameter values" "true"
abstract class Test {
    int foo(boolean... args) {
      return foo(<selection>0<caret></selection>, args);
  }

    abstract int foo(int ii, boolean... args);
}