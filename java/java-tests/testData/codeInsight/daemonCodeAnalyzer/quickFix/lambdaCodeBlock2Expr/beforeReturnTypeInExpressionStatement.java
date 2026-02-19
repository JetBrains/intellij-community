// "Replace with expression lambda" "true-preview"
class Test {
  {
    Runnable c = () -> <caret>{foo();};
  }
  
  int foo() {return 1;}
}