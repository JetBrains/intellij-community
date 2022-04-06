class X {
  int <warning descr="Non-ASCII characters">Ж</warning> = 0;
  class Inner<warning descr="Mixed languages: CYRILLIC symbols found in LATIN word"><warning descr="Non-ASCII characters">П</warning></warning> {}
  // comment<warning descr="Mixed languages: CYRILLIC symbols found in LATIN word"><warning descr="Non-ASCII characters">жп</warning></warning> 234
  String s = "12<warning descr="Mixed languages: CYRILLIC symbols found in LATIN word"><warning descr="Non-ASCII characters">л</warning></warning>TO<warning descr="Non-ASCII characters">орыва</warning>0";
  void <warning descr="Non-ASCII characters">жжж</warning>() {
    <warning descr="Non-ASCII characters">жжж</warning>();
  }
}