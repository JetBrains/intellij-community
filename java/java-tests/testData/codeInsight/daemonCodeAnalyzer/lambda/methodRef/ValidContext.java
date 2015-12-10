public class Test {
  {
    if (<error descr="Method reference expression is not expected here">Test::length instanceof String</error>) {
    }
    bar(Test::length);
  }

  public static Integer length(String s) {
    return s.length();
  }
  
  public static void bar(Bar bar) {}
  
  interface Bar {
    Integer _(String s);
  }
}