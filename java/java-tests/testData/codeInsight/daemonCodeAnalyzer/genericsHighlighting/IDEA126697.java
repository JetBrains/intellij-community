class Test {
  private static void <warning descr="Private method 'test(java.lang.Short)' is never used">test</warning>(Short s) { System.out.println("Short:" + s); }
  private static void test(short s) { System.out.println("short:" + s); }

  public static void main(String ... args) {
    test(true ? new Short((short) 1) : new Byte((byte) 1));
  }
}