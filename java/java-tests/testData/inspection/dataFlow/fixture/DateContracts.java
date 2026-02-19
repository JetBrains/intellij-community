import java.util.*;

class X {
  void test(Date d1, Date d2) {
    if (d1.before(d2)) {
      if (<warning descr="Condition 'd1 == d2' is always 'false'">d1 == d2</warning>) {}
    }
    if (<warning descr="Condition 'd1.after(d1)' is always 'false'">d1.after(d1)</warning>) {}
  }
}