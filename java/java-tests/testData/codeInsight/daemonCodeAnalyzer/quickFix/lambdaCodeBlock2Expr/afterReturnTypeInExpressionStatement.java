// "Replace with expression lambda" "true"
class Test {
  {
    Runnable c = () -> foo();
  }
  
  int foo() {return 1;}
}