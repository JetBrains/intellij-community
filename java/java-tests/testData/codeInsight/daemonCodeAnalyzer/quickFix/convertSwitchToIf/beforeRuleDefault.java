// "Replace 'switch' with 'if'" "true"
class Test {
  void foo(int x) {
    switch<caret> (x) {
      //1
      case /*2*/0/*3*/ -> /*4*/System.out.println("zero");/*5*/
      default /*6*/->/*7*/ System.out.println("non-zero");/*8*/
    }
  }
}