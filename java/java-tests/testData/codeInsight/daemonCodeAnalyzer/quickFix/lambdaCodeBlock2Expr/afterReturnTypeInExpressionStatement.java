// "Replace with expression lambda" "true-preview"
class Test {
  {
    Runnable c = () -> foo();
  }
  
  int foo() {return 1;}
}