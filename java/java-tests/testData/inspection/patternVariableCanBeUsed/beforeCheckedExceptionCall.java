// "Replace 'integer' with pattern variable" "false"

class X {
  public static void testWithExceptionCall() throws Exception {
    Object object = new Object();
    if (!(object instanceof Integer)) {
      test();
    }
    Integer int<caret>eger = (Integer)object;
    System.out.println(integer);
  }

  private static void test() throws Exception {
  }
}