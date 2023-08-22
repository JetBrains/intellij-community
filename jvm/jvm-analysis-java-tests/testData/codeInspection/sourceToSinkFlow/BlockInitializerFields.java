import org.checkerframework.checker.tainting.qual.Tainted;
import org.checkerframework.checker.tainting.qual.Untainted;

class Main {

  private String field;
  private String field2;
  {
    field = getFromSomething();
    field2 = "";
  }

  private void test() {
    sink(<warning descr="Unknown string is used as safe parameter">field</warning>);
    sink(field2);
  }

  @Tainted
  private String getFromSomething() {
    return "";
  }

  private void sink(@Untainted String a) {

  }
}