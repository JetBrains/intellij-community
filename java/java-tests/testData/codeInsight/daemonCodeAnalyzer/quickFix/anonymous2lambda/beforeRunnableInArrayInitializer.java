// "Replace with lambda" "true-preview"
class Test {
  {
    Runnable[] r = new Runnable[] {new Run<caret>nable() {
      @Override
      public void run() {
        System.out.println("");
      }
    }};
  }
}