// "Generate overloaded method with default parameter values" "true"
class Test {
  <T> int foo(T i<caret>i, boolean... args){
    return 1;
  }
}