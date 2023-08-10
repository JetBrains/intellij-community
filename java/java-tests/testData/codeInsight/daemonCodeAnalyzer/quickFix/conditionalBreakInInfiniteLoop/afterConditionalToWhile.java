// "Move condition to loop" "true-preview"
class Main {
  public static void main(String[] args) {
    int i = 1;
      /*1*/
      /*2*/
      /*3*/
      /*7*/
      /*8*/
      while (i % 10 != 0 && i < 12) {
          /*4*/
          i =/*5*/ i * 2;/*6*/
      }
  }
}