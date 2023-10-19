import org.checkerframework.checker.tainting.qual.Untainted;

class IfStatement {
  public void test1(String a) {
    sink(<warning descr="Unknown string is used as safe parameter">a</warning>); //warn
  }

  public void test2(String a) {
    String bar;
    bar = doSomething1(a);
    sink(bar);
  }

  public void test3(String a) {
    String bar;
    bar = doSomething2(a);
    sink(<warning descr="Unknown string is used as safe parameter">bar</warning>);
  }

  private static String doSomething1(String param) {

    int num = 106;

    if ((7 * 18) + num > 100) {
      return "This_should_always_happen";
    }
    return param;
  }

  private static String doSomething2(String param) {

    int num = 106;

    if ((7 * 18) + num > 500) {
      return "This_should_always_happen";
    }
    return param;
  }

  public static void sink(@Untainted String t) {

  }
}
