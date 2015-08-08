// "Generate overloaded method with default parameter value" "true"
class Test {
    <T> int foo(boolean... args) {
      return foo(<caret>, args);
  }

    <T> int foo(T ii, boolean... args){
    return 1;
  }
}