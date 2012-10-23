// "Replace with lambda" "false"
class Test {
  {
    new Ru<caret>nnable() {
        @Override
        public void run() {
            Class c = getClass();
        }
    };
  }
}