import org.checkerframework.checker.tainting.qual.Untainted;

class IfStatement {
  public void test1(String a) {
    sink(<warning descr="Unknown string is used as safe parameter">a</warning>); //warn
  }

  public void test2(String a) {
    String bar;
    int num = 86;
    bar = (7 * 42) - num > 200 ? "This_should_always_happen" : a;
    sink(bar);
  }
  public void test3(String a) {
    String bar;
    int num = 86;
    bar = (7 * 42) - num > 300 ? "This_should_always_happen" : a;
    sink(<warning descr="Unknown string is used as safe parameter">bar</warning>);
  }
  public static void sink(@Untainted String t) {

  }
}
