// "Replace with lambda" "true-preview"

class Test {
  Runnable r = new//comment inside new expression
    Ru<caret>nnable() {
   //my comment
    public void run () {}
  };
}