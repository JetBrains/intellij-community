// "Move condition to loop" "false"
class Main {
  public static void main(String[] args) {
    boolean isValid = true;
    boolean isEnabled = true;
    int i = 1;
    while<caret>(i % 10 != 0 && i < 100/*7*/) /*8*/ {
      if(isValid/*9*/ ||/*10*/ isEnabled/*1*/)/*2*/ break;/*3*/
      /*4*/
      i =/*5*/ i * 2;/*6*/
      }
  }
}