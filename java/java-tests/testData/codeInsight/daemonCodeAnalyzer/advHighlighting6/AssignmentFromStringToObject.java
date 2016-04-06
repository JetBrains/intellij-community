class Test {
  {
    Object o = "";
    <warning descr="Operator '+' cannot be applied to 'java.lang.Object', 'java.lang.String'">o += ""</warning>;
    System.out.println(o);
  }
}