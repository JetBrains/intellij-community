class Test {
  public static void main(String[] args) {
    Object o = null;
    if (<error descr="Operator '==' cannot be applied to 'java.lang.Object', 'int'">o == 1</error>) {}
    if (1 == o) {}
  }
}