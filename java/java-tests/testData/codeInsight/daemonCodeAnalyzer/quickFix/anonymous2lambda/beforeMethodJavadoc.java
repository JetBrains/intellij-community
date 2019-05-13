// "Replace with lambda" "false"
class Test {
  {
    Runnable r = new Ru<caret>nnable() {
        /**
         * important javadoc
         */
        @Override
        public void run() {
        }
    };
  }
}