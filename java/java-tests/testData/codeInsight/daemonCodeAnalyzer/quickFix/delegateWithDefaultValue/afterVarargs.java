// "Generate overloaded method with default parameter values" "true"
class Test {
    int foo(boolean... args) {
      return foo(<selection>0<caret></selection>, args);
  }

    int foo(int ii, boolean... args){
    return 1;
  }
}