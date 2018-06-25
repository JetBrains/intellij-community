import java.util.List;

class Test {
  void test(Object x, Object y) {
    if(x.equals(x)) {
      // do not report here; reported by EqualsWithItselfInspection
      System.out.println("always");
    }
    if(!x.equals(x)) {
      // do not report here; reported by EqualsWithItselfInspection
      System.out.println("never");
    }
    y = x;
    if(<warning descr="Condition 'x.equals(y)' is always 'true'">x.equals(y)</warning>) {
      System.out.println("always");
    }
  }
}