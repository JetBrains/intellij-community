// "Replace with expression lambda" "false"
class Test {
  {
    Runnable c = () -> <caret>{foo();};
  }
  
  int foo() {return 1;}
}