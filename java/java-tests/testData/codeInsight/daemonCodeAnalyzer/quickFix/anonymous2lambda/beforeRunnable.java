// "Replace with lambda" "true"
class Test {
  {
    Runnable r = new Run<caret>nable() {
      @Override
      public void run() {
        System.out.println("");
      }
    };
  }
}