import java.util.Date;

class DataFlowBug {

  private static boolean isNumberable(Object o) {
    return o instanceof Number;
  }

  public Object add(Object left, Object right) {
    if (left != null && right != null && (left instanceof Date || right instanceof Date)) {
      if (isNumberable(left)) {
        return ((<warning descr="Casting 'right' to 'Date' may produce 'java.lang.ClassCastException'">Date</warning>) right).getTime();
      }

      if (isNumberable(right)) {
        return ((<warning descr="Casting 'left' to 'Date' may produce 'java.lang.ClassCastException'">Date</warning>) left).getTime();
      }
    }
    return new Object();
  }

}