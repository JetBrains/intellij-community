// "Move condition to loop" "true-preview"
class Main {
  public static void main(String[] args) {
    boolean isValid = true;
    boolean isEnabled = true;
    int i = 1;
    while<caret>(isValid || isEnabled/*7*/) /*8*/ {
      if(i >= 12/*1*/)/*2*/ break;/*3*/
      /*4*/
      i =/*5*/ i * 2;/*6*/
      }
  }
}