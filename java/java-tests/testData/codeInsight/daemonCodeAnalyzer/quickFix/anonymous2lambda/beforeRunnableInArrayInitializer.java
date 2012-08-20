// "Replace with lambda" "true"
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