class Test {

  static class Object1 {
  }

  static class Object2 {
    static <T extends Object1> T o2() {
      return null;
    }
  }

  private static void method1(Object1 o) {
    System.out.println(o);
  }

  private static void <warning descr="Private method 'method1(java.lang.String)' is never used">method1</warning>(String o) {
    System.out.println(o);
  }

  static {
    method1(Object2.o2());
  }
}