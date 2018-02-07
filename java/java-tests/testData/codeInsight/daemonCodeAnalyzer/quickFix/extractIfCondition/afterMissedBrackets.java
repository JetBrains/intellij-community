// "Extract if (a)" "true"
class TestThreadInspection {
  void f(boolean a, boolean b, boolean c){
      if (a)
          if (b) {
              System.out.println("a&b");//first comment
          } else {
              if (c) {
                  System.out.println("c");
              }
          }
      else if (c) {
          System.out.println("c");
      }
  }
}