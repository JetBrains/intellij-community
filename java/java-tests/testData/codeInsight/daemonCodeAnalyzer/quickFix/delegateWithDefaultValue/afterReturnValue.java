// "Generate overloaded method with default parameter values" "true"
class Test {
    int foo() {
      return foo(<caret>);
  }

    int foo(int ii){
    return 1;
  }
}