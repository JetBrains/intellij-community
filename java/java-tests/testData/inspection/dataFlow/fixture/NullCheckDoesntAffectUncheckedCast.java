class DataFlowBug {

  public int add2(Object left, Object right) {
    if (left != null && !(left instanceof String)) {
      return ((<warning descr="Casting 'left' to 'String' will produce 'ClassCastException' for any non-null value">String</warning>) left).length();

    }
    if (!(right instanceof String)) {
      return ((<warning descr="Casting 'right' to 'String' will produce 'ClassCastException' for any non-null value">String</warning>) right).length();

    }
    return 2;
  }

}