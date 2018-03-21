class Test {
  public static void foo(Object[] objectArray, boolean check) {
    String kv = (String)(check ? "N" : objectArray[0]) ;
  }
}
class PolyConditional {

    public static void callee(String str) { }
    public void usage(Object obj) {
        callee((String) (obj != null ? obj : null));
    }

    public static void callee1(String str) { }
    public void usage1(Object obj) {
        callee1((String) (obj != null ? obj : null));
    }
}