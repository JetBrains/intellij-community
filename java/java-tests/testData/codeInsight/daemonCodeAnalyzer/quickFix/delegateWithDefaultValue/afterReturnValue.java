// "Generate overloaded method with default parameter values" "true"
class Test {
    int foo() {
      return foo(<selection>0<caret></selection>);
  }

    int foo(int ii){
    return 1;
  }
}