import org.jetbrains.annotations.NotNull;

class Test {

  public static void main(String[] args) {
  }

  private static void test(Object o) {
    for (int i = 0; i < 10; i++) {
      System.out.println(1);
      System.out.println(2);
      System.out.println(3);
      System.out.println(4);
      System.out.println(5);
      System.out.println(6);
      System.out.println(7);
      System.out.println(8);
      System.out.println(9);
      System.out.println(10);

        String s = getString<caret>(o);

        System.out.println(s);
      System.out.println(s);
      System.out.println(s);
      System.out.println(s);
      System.out.println(s);
      System.out.println(s);
      System.out.println(s);
      System.out.println(s);
      System.out.println(s);
      System.out.println(s);
      System.out.println(s);
      System.out.println(s);
      System.out.println(s);
    }
  }

    @NotNull
    private static String getString(Object o) {
        String s;
        if (o instanceof String s2 && s2.length() == 1) {
          s = "1";
        } else if (o instanceof String s2 && s2.length() == 2) {
          s = "2";
        } else if (o instanceof String s2 && s2.length() == 3) {
          s = "3";
        } else if (o instanceof String s2 && s2.length() == 4) {
          s = "4";
        } else if (o instanceof String s2 && s2.length() == 5) {
          s = "5";
        } else if (o instanceof String s2 && s2.length() == 6) {
          s = "6";
        } else if (o instanceof String s2 && s2.length() == 7) {
          s = "7";
        } else if (o instanceof String s2 && s2.length() == 8) {
          s = "8";
        } else {
          s = "null";
        }
        return s;
    }
}
