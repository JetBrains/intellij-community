class MyTest {

  static void n(boolean f) {
    m(f ? (f ? 0 : "-") : "");
    m(f ? 0  : "");
    m1(f ? (f ? <error descr="Incompatible types. Found: 'int', required: 'java.lang.String'">0</error> : "-") : "");
    m1(f ? <error descr="Incompatible types. Found: 'int', required: 'java.lang.String'">0</error>  : "");
  }

  static void m(String s) {}
  static void m(Object o) {}
  
  static void m1(String o) {}
}