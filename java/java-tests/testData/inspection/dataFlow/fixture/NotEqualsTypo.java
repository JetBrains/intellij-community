class Some {
  boolean foo(Object first, Object second) {
    boolean isEqual = true;

    if (<error descr="Operator '||' cannot be applied to 'boolean', 'java.lang.Object'">first != null || second</error> -= null) {
      return isEqual;
    }

    return isEqual;
  }

}
