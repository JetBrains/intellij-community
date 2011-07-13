class XX {
  public static String foo(Properties p, String s, boolean b){return null;}
  public static String foo(Map p, String s, boolean b){return null;}
}
class UU {
  void bar() {
      Properties p = new Properties();
      XX.fo<ref>o(p, "xxx", false);
  }
}
