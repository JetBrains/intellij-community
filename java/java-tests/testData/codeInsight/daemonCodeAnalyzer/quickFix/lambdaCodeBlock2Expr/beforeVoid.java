// "Replace with expression lambda" "true"
class Test {
  {
    Runnable c = () -> <caret>{System.out.println();};
  }
}