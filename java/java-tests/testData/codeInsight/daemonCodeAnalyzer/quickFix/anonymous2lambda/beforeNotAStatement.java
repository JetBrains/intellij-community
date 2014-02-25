// "Replace with lambda" "false"
class Test {
  interface I {

  }
  interface Bar extends I {
    int compare(String o1, String o2);
  }
  {
    new Ba<caret>r() {
      @Override
      public int compare(String o1, String o2) {
        return 0;
      }
    };
  }
}