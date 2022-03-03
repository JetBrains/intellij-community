// "Generate overloaded method with default parameter values" "true"
class Test {
    <T> int foo(boolean... args) {
      return foo(<selection>null<caret></selection>, args);
  }

    <T> int foo(T ii, boolean... args){
    return 1;
  }
}