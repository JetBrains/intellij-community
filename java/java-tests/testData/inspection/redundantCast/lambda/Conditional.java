class Test {
  public static void foo(Object[] objectArray, boolean check) {
    String kv = (String)(check ? "N" : objectArray[0]) ;
  }
}
