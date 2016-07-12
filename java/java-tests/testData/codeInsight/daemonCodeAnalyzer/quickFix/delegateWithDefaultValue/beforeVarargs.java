// "Generate overloaded method with default parameter values" "true"
class Test {
  int foo(int i<caret>i, boolean... args){
    return 1;
  }
}