class X {
  int <warning descr="Non-ASCII characters">Ж</warning> = 0;
  class Inner<warning descr="Non-ASCII characters">П</warning> {}
  // comment<warning descr="Non-ASCII characters">жп</warning> 234
  String s = "12<warning descr="Non-ASCII characters">л</warning>3<warning descr="Non-ASCII characters">орыва</warning>0";
  void <warning descr="Non-ASCII characters">жжж</warning>() {
    <warning descr="Non-ASCII characters">жжж</warning>();
    String s = "<warning descr="Non-ASCII characters">С</warning>ore";
    System.out.println("<warning descr="Non-ASCII characters">С</warning>ore");
    String u = "\5\0\51\2\3\0\136\2\21\0\33\2\65\0\20\2\u0200\0\u19b6\2"; // ignore slash u
  }
}