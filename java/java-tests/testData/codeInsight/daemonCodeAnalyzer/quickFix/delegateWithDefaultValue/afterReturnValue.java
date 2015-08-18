// "Generate overloaded method with default parameter value" "true"
class Test {
    int foo() {
      return foo(<caret>);
  }

    int foo(int ii){
    return 1;
  }
}