// "Replace with lambda" "false"
class Test {
  {
    Runnable a = new Run<caret>nable() {
        @Override
        public void run()
    };
  }
}