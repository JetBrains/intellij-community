// "Move condition to loop" "true"
class Main {
  public static void main(String[] args) {
    int i = 0;
    while(true/*7*/) {
      if<caret>(i >= 12/*1*/)/*2*/ break;/*3*/
      /*4*/
      i =/*5*/ i + 1;/*6*/
      }
  }
}