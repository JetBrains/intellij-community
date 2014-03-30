// "Replace with lambda" "true"
class X1 {
  Runnable m() {
    String s;
    return new Run<caret>nable() {
      @Override
      public void run() {
        String s;
      }
    };
  }
}