class Test {
  {
    Object o = "";
    <warning descr="Operator '+' cannot be applied to 'java.lang.Object', 'java.lang.String'">o += ""</warning>;
    System.out.println(o);

    CharSequence c = "";
    c += "";
    System.out.println(c);
  }
}