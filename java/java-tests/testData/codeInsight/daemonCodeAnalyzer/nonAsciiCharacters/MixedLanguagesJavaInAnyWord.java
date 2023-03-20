class X {
  int Ж = 0;
  class Inner<warning descr="Non-ASCII symbols in ASCII word">П</warning> {}
  // comment<warning descr="Non-ASCII symbols in ASCII word">жп</warning> 234
  String sameLang = "12л3орыва0";
  String mixed = "12<warning descr="Non-ASCII symbols in ASCII word">че</warning>to3";
  void жжж() {
    жжж();
    String core = "<warning descr="Non-ASCII symbols in ASCII word">С</warning>ore";
    System.out.println("<warning descr="Non-ASCII symbols in ASCII word">С</warning>ore");
    String u = "\5\0\51\2\3\0\136\2\21\0\33\2\65\0\20\2\u0200\0\u19b6\2"; // ignore slash u
  }
}