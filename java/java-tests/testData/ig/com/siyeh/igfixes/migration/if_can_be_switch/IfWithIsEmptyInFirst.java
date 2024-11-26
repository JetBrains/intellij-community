class Comments {

  public static void test2(String str) {
    i<caret>f (str.isEmpty()) {
      System.out.println(20);
    } else if (str.equals("20")) {
      System.out.println(30);
    } else if (str.equals("30")) {
      System.out.println(298);
    } else {
      throw new IllegalStateException("Unexpected value: " + str);
    }
  }
}