// "Extract if (a)" "true"
class TestThreadInspection {
  void f(boolean a, boolean b, boolean c){
      if (a) {
          if (b) {
              System.out.println("a&b");
          } else {
              System.out.println("c");
          }
      } else {
          System.out.println("c");
      }
  }
}