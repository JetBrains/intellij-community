// "Replace with one line expression" "false"
class Test {
  {
    Runnable c = () -> <caret>{foo();};
  }
  
  int foo() {return 1;}
}