// "Move condition to loop" "true-preview"
class Main {
  public static void main(String[] args) {
    int i = 1;
      /*1*/
      /*2*/
      /*3*/
      /*7*/
      /*8*/
      do {
          i =/*5*/ i * 2;/*6*/
          /*4*/
      } while (i < 12 && i % 10 != 0);
  }
}