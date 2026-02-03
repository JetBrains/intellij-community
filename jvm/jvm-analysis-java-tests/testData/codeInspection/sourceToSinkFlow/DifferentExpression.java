import org.checkerframework.checker.tainting.qual.Untainted;

public class DifferentExpression {

  public void test() {
    sink(this.toString());
    Runnable r = () -> {
    };
    sink(<warning descr="Unknown string is used as safe parameter">r.toString()</warning>); //warn
    sink(DifferentExpression.class.toString());
    sink("test" + (1 - 1));
    int x = 1;
    sink("test" + (++x));
    sink(<warning descr="Unknown string is used as safe parameter">param2("1",<error descr="Expression expected"> </error>)</warning>); //warn
  }


  public static void sink(@Untainted String string) {

  }

  public static String param2(String t, String t1) {
    return t1;
  }
}
