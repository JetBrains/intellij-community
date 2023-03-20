// "Move condition to loop" "true-preview"
class Main {
  public static void main(String[] args) {
    boolean isValid = true;
    boolean isEnabled = true;
    int i = 1;
      /*1*/
      /*2*/
      /*3*/
      /*7*/
      /*8*/
      while ((isValid || isEnabled) && i < 12) {
          /*4*/
          i =/*5*/ i * 2;/*6*/
      }
  }
}