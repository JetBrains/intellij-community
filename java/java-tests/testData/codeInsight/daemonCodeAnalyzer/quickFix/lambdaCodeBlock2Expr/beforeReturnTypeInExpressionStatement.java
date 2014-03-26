// "Replace with expression lambda" "true"
class Test {
  {
    Runnable c = () -> <caret>{foo();};
  }
  
  int foo() {return 1;}
}