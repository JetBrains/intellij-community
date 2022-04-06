class X {
  int Ж = 0;
  class Inner<warning descr="Mixed languages: CYRILLIC symbols found in LATIN word">П</warning> {}
  // comment<warning descr="Mixed languages: CYRILLIC symbols found in LATIN word">жп</warning> 234
  String sameLang = "12л3орыва0";
  String mixed = "12<warning descr="Mixed languages: CYRILLIC symbols found in LATIN word">че</warning>to3";
  void жжж() {
    жжж();
    String core = "<warning descr="Mixed languages: CYRILLIC symbols found in LATIN word">С</warning>ore";
    System.out.println("<warning descr="Mixed languages: CYRILLIC symbols found in LATIN word">С</warning>ore");
    String u = "\5\0\51\2\3\0\136\2\21\0\33\2\65\0\20\2\u0200\0\u19b6\2"; // ignore slash u
  }
}