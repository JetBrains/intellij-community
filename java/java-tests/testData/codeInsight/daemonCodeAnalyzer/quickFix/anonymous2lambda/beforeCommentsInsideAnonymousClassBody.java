// "Replace with lambda" "true"

class Test {
  Runnable r = new Ru<caret>nnable() {
    //my comment
    public void run () {}
  };
}