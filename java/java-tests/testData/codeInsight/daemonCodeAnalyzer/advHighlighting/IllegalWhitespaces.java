class C {
  void m() {
    f(1,<error descr="Expression expected"><error descr="Illegal character: U+00A0"> </error></error><error descr="',' or ')' expected">2</error>);
  }

  void f(int x, int y) {
    if (x == 0 ||<error descr="')' expected"><error descr="Expression expected"><error descr="Illegal character: U+00A0"> </error></error></error>y == 0<error descr="';' expected"><error descr="Unexpected token">)</error></error> { }
  }
}