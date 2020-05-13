class X {
  int <warning descr="Non-ASCII characters in an identifier">Ж</warning> = 0;
  class <warning descr="Non-ASCII characters in an identifier"><warning descr="Symbols from different languages found: [LATIN, CYRILLIC]">InnerП</warning></warning> {}
  // comment<warning descr="Non-ASCII characters in a comment">жп</warning> 234
  String s = "12<warning descr="Non-ASCII characters in a string literal">л</warning>3<warning descr="Non-ASCII characters in a string literal">орыва</warning>0";
  void <warning descr="Non-ASCII characters in an identifier">жжж</warning>() {
    жжж();
  }
}