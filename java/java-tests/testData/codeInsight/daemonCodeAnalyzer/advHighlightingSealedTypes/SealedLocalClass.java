class Sealed {

  void x() {
    <error descr="Modifier 'sealed' not allowed on local classes">sealed</error> class X {}
    <error descr="Modifier 'non-sealed' not allowed on local classes">non-sealed</error> class Y {}
  }
}