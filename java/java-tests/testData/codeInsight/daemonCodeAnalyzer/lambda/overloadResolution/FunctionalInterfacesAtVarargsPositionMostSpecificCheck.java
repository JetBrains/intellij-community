class Test {
  private static <T> void test(Class<T> cls, Runnable... objs) {
    System.out.println(cls);
    System.out.println(objs);
  }

  private static <K> void <warning descr="Private method 'test(K, java.lang.Runnable...)' is never used">test</warning>(K obj, Runnable... objs) {
    System.out.println(obj);
    System.out.println(objs);
  }

  public static void main(String[] args) {
    test(String.class, new Runnable[1]);
  }
}