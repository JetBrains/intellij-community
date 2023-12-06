import org.checkerframework.checker.tainting.qual.Untainted;

class IfStatement {
  public void test1(String a) {
    sink(<warning descr="Unknown string is used as safe parameter">a</warning>); //warn
  }

  public void test2(String a) {
    a = "2";
    sink(a); //no
  }

  public void test3(String a) {
    a = a.length() == 1 ? "3" : a;
    sink(<warning descr="Unknown string is used as safe parameter">a</warning>); //warn
  }
  public void test4(String a) {
    a = a.length() == 1 ? a : "3";
    sink(<warning descr="Unknown string is used as safe parameter">a</warning>); //warn
  }
  public void test5(String a) {
    a = a.length() == 1 ? "3" : "a";
    sink(a); //no
  }
  public static void sink(@Untainted String t) {

  }
}
