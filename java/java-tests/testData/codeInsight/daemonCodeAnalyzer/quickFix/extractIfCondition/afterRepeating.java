// "Extract if (a)" "true"
class Test {
  void f(boolean a, boolean b, boolean c){
      /*2*/
      if (a) {
          if (b) {
              System.out.println("a&b");//first comment
          } else if (c) {
              System.out.println("a&c"); // three
          }
      }
  }
}