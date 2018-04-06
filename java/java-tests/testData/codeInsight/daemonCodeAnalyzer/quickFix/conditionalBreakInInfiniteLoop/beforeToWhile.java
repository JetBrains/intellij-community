// "Move condition to loop" "true"
class Main {
  public static void main(String[] args) {
    int i = 0;
    while<caret>(true/*7*/) /*8*/ {
      if(i >= 12/*1*/)/*2*/ break;/*3*/
      /*4*/
      i =/*5*/ i + 1;/*6*/
      }
  }
}