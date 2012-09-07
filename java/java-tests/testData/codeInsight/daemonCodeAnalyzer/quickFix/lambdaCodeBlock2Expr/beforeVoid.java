// "Replace with one line expression" "true"
class Test {
  {
    Runnable c = () -> <caret>{System.out.println();};
  }
}