class Vararg {

  void x(<error descr="Vararg parameter must be the last in the list">int... ns</error>, boolean b) {}
  void y(int... ns<error descr="C-style array declaration not allowed in vararg parameter">[][]</error>) {}
  void z(int[]... ns<error descr="C-style array declaration not allowed in vararg parameter">[]</error>) {}
}