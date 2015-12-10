public class Test {
  {
    if (Test::<error descr="Cannot resolve method 'length'">length</error> instanceof String) {
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