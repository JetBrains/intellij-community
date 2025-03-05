class C {
  void foo(int i, int j) {
    <error descr="Not a statement">123</error>
    <error descr="Not a statement">i+j /*oops*/</error>
    foo(1, 2)<EOLError descr="';' expected"></EOLError>
    i++<EOLError descr="';' expected"></EOLError>
    toString()<error descr="';' expected"> </error>/*oops*/
  }
}