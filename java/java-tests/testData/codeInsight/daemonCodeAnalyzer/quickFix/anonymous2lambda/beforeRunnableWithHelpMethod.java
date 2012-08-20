// "Replace with lambda" "false"
class Test {
  {
    Runnable r = new Ru<caret>nnable() {
        @Override
        public void run() {
           helper();
        }
        private void helper(){}
    };
  }
}