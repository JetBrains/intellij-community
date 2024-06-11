// "Add 'finally' block" "true-preview"
class X {
  void test() {
      try {
          System.out.println();
      } // todo
      finally {
          <caret>
      }
  }
}