// "Extract if (a)" "true-preview"
class TestThreadInspection {
  void f(boolean a, boolean b, boolean c){
      if (a) {
          System.out.println("a&b");
      } else if (b) {
          System.out.println("a&b");
      } else {
          if (c) {
              System.out.println("c");
          }
      }
  }
}