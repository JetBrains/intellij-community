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
      while (i % 10 != 0 && (!isValid/*9*/ ||/*10*/ !isEnabled)) {
          /*4*/
          i =/*5*/ i * 2;/*6*/
      }
  }
}