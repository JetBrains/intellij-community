class X {
  int <warning descr="Non-ASCII characters in an identifier">Ж</warning> = 0;
  class <warning descr="Non-ASCII characters in an identifier"><warning descr="Symbols from different languages found: [LATIN, CYRILLIC]">InnerП</warning></warning> {}
  // comment<warning descr="Non-ASCII characters in a comment">жп</warning> 234
  String s = "12<warning descr="Non-ASCII characters in a string literal">л</warning>3<warning descr="Non-ASCII characters in a string literal">орыва</warning>0";
  void <warning descr="Non-ASCII characters in an identifier">жжж</warning>() {
    жжж();
    String s = <warning descr="Symbols from different languages found: [LATIN, CYRILLIC]">"<warning descr="Non-ASCII characters in a string literal">С</warning>ore"</warning>;
    System.out.println(<warning descr="Symbols from different languages found: [LATIN, CYRILLIC]">"<warning descr="Non-ASCII characters in a string literal">С</warning>ore"</warning>);
    String u = "\5\0\51\2\3\0\136\2\21\0\33\2\65\0\20\2\u0200\0\u19b6\2"; // ignore slash u
  }
}