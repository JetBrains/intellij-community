public class DuplicateFallThrough {

  public static void test(int x) {
    switch (x) {
      case 0:
        System.out.println("a");
      case 1:
        System.out.println("b");
        break;
      case 2:
        System.out.println("a");
      case 3:
        System.out.println("c");
        break;
      default:
        System.out.println("d");
    }
  }
}