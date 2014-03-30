class DataFlowBug {

  public int add2(Object left, Object right) {
    if (left != null && !(left instanceof String)) {
      return ((<warning descr="Casting 'left' to 'String' may produce 'java.lang.ClassCastException'">String</warning>) left).length();

    }
    if (!(right instanceof String)) {
      return ((<warning descr="Casting 'right' to 'String' may produce 'java.lang.ClassCastException'">String</warning>) right).length();

    }
    return 2;
  }

}