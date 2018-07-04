// "Extract if (a)" "true"
class Test {
  void f(boolean a, boolean b, boolean c, boolean d){
      if (a) {
          if (b) {
              System.out.println("a&b");//first comment
          } else {
              System.out.println("a");//a
          }
      } else if (a && c) {
          System.out.println("a&c");//ac
      } else if (a && d) {
          System.out.println("a&d");//ad
      }
  }
}