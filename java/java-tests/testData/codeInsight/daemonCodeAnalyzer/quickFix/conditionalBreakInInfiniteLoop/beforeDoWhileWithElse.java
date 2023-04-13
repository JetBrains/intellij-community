// "Move condition to loop" "false"
class Main {
  private static int getI() {
    return 0;
  }

  public static void test3() {
    int i = getI();
    do {
      System.out.println(1);
      if (i == 1) {
        break;
      }else{
        System.out.println(1);
      }
    } while<caret> (true);
  }
}