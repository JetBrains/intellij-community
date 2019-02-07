// "Replace 'switch' with 'if'" "true"
class Test {
  void foo(int x) {
      //1
      /*2*/
      /*3*/
      /*4*/
      /*6*/
      /*7*/
      if (x == 0) {
          System.out.println("zero");/*5*/
      } else {
          System.out.println("non-zero");/*8*/
      }
  }
}