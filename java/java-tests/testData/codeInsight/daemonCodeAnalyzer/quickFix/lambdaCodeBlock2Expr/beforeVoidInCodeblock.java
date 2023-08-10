// "Replace with expression lambda" "true-preview"
class Test {
  {
    Runnable c = () -> <caret>{{System.out.println();}};
  }
}