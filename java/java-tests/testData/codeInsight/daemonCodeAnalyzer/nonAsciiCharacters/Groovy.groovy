class X {
  int <warning descr="Non-ASCII characters">Ж</warning> = 0;
  class Inner<warning descr="Non-ASCII characters"><warning descr="Non-ASCII symbols in ASCII word">П</warning></warning> {}
  // comment<warning descr="Non-ASCII characters"><warning descr="Non-ASCII symbols in ASCII word">жп</warning></warning> 234
  String s = "12<warning descr="Non-ASCII characters"><warning descr="Non-ASCII symbols in ASCII word">л</warning></warning>TO<warning descr="Non-ASCII characters"><warning descr="Non-ASCII symbols in ASCII word">орыва</warning></warning>0";
  void <warning descr="Non-ASCII characters">жжж</warning>() {
    <warning descr="Non-ASCII characters">жжж</warning>();
  }
}