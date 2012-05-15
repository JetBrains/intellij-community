package pkg;

/**
 * @see #<error descr="Cannot resolve symbol 'method(B1.C)'">method</error>(B1.C)
 * @see #<error descr="Cannot resolve symbol 'method(B1.C[][])'">method</error>(B1.C[][])
 * @see #<error descr="Cannot resolve symbol 'method(B1.C..)'">method</error>(<error descr="Cannot resolve symbol 'B1.C.'">B1.C.</error>.)
 * @see #<error descr="Cannot resolve symbol 'method(B1.C[)'">method</error>(B1.C[)
 */
class A1 {
  public void method(B1.C[] c) { }
}

class B1 {
  class C {
  }
}
