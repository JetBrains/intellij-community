import java.io.*;

class C {
  static class E extends Exception { }
  static class RE extends RuntimeException { }

  void f() { }
  void g() throws E { }
  void h() throws RE { }

  void m0() {
    try { throw new FileNotFoundException(); }
    catch (FileNotFoundException e) { }
    <warning descr="Unreachable section: exception 'java.io.FileNotFoundException' has already been caught">catch (IOException e) { }</warning>
  }

  void m1() {
    try { throw new IOException(); }
    catch (FileNotFoundException e) { }
    catch (IOException e) { }
  }

  void m2() {
    try { f(); }
    catch (Exception e) { }
  }

  void m3() {
    try { g(); }
    catch (Exception e) { }
  }

  void m4() {
    try { h(); }
    catch (Exception e) { }
  }

  void m5() {
    try { f(); }
    catch (Throwable t) { }
  }

  void m6() {
    try { g(); }
    catch (Throwable t) { }
  }

  void m7() {
    try { h(); }
    catch (Throwable t) { }
  }

  void m9() {
    try { f(); }
    catch (Error e) { }
    catch (Throwable t) { }
  }

  void m10() {
    try { g(); }
    catch (Error e) { }
    catch (Throwable t) { }
  }

  void m11() {
    try { h(); }
    catch (Error e) { }
    catch (Throwable t) { }
  }

  void m12() {
    try { f(); }
    catch (RuntimeException e) { }
    catch (Throwable t) { }
  }

  void m13() {
    try { g(); }
    catch (RuntimeException e) { }
    catch (Throwable t) { }
  }

  void m14() {
    try { h(); }
    catch (RuntimeException e) { }
    catch (Throwable t) { }
  }

  void m15() {
    try { f(); }
    catch (RuntimeException e) { }
    <warning descr="Unreachable section: exception 'java.lang.RuntimeException' has already been caught">catch (Exception e) { }</warning>
  }

  void m16() {
    try { g(); }
    catch (RuntimeException e) { }
    catch (Exception e) { }
  }

  void m17() {
    try { h(); }
    catch (RuntimeException e) { }
    <warning descr="Unreachable section: exception 'java.lang.RuntimeException' has already been caught">catch (Exception e) { }</warning>
  }

  void m18() {
    try { f(); }
    catch (RuntimeException e) { }
    catch (<error descr="Exception 'C.E' is never thrown in the corresponding try block">E e</error>) { }
    <warning descr="Unreachable section: exception 'java.lang.RuntimeException' has already been caught">catch (Exception e) { }</warning>
  }

  void m19() {
    try { g(); }
    catch (RuntimeException e) { }
    catch (E e) { }
    <warning descr="Unreachable section: exceptions 'C.E, java.lang.RuntimeException' have already been caught">catch (Exception e) { }</warning>
  }

  void m20() {
    try { h(); }
    catch (RuntimeException e) { }
    catch (<error descr="Exception 'C.E' is never thrown in the corresponding try block">E e</error>) { }
    <warning descr="Unreachable section: exception 'java.lang.RuntimeException' has already been caught">catch (Exception e) { }</warning>
  }

  void m21() {
    try { f(); }
    catch (RuntimeException e) { }
    <warning descr="Unreachable section: exception 'java.lang.RuntimeException' has already been caught">catch (Exception e) { }</warning>
  }

  void m22() {
    try { g(); }
    catch (RuntimeException e) { }
    catch (Exception e) { }
  }

  void m23() {
    try { h(); }
    catch (RuntimeException e) { }
    <warning descr="Unreachable section: exception 'java.lang.RuntimeException' has already been caught">catch (Exception e) { }</warning>
  }

  void m24() {
    try { f(); }
    catch (RuntimeException e) { }
    catch (Error e) { }
    <warning descr="Unreachable section: exceptions 'java.lang.RuntimeException, java.lang.Error' have already been caught">catch (Throwable t) { }</warning>
  }

  void m25() {
    try { g(); }
    catch (RuntimeException e) { }
    catch (Error e) { }
    catch (Throwable t) { }
  }

  void m26() {
    try { h(); }
    catch (RuntimeException e) { }
    catch (Error e) { }
    <warning descr="Unreachable section: exceptions 'java.lang.RuntimeException, java.lang.Error' have already been caught">catch (Throwable t) { }</warning>
  }

  void m27() {
    try { f(); }
    catch (RuntimeException e) { }
    catch (Error e) { }
    catch (<error descr="Exception 'C.E' is never thrown in the corresponding try block">E e</error>) { }
    <warning descr="Unreachable section: exceptions 'java.lang.RuntimeException, java.lang.Error' have already been caught">catch (Throwable t) { }</warning>
  }

  void m28() {
    try { g(); }
    catch (RuntimeException e) { }
    catch (Error e) { }
    catch (E e) { }
    <warning descr="Unreachable section: exceptions 'C.E, java.lang.RuntimeException, java.lang.Error' have already been caught">catch (Throwable t) { }</warning>
  }

  void m29() {
    try { h(); }
    catch (RuntimeException e) { }
    catch (Error e) { }
    catch (<error descr="Exception 'C.E' is never thrown in the corresponding try block">E e</error>) { }
    <warning descr="Unreachable section: exceptions 'java.lang.RuntimeException, java.lang.Error' have already been caught">catch (Throwable t) { }</warning>
  }

  void m30() {
    try { f(); }
    catch (RuntimeException e) { }
    catch (Error e) { }
    <warning descr="Unreachable section: exceptions 'java.lang.RuntimeException, java.lang.Error' have already been caught">catch (Throwable t) { }</warning>
  }

  void m31() {
    try { g(); }
    catch (RuntimeException e) { }
    catch (Error e) { }
    catch (Throwable t) { }
  }

  void m32() {
    try { h(); }
    catch (RuntimeException e) { }
    catch (Error e) { }
    <warning descr="Unreachable section: exceptions 'java.lang.RuntimeException, java.lang.Error' have already been caught">catch (Throwable t) { }</warning>
  }

  void m33() {
    try { g(); }
    catch (E e) { }
  }

  void m34() {
    try { h(); }
    catch (<error descr="Exception 'C.E' is never thrown in the corresponding try block">E e</error>) { }
  }

  void m35() {
    try { f(); }
    catch (<error descr="Exception 'C.E' is never thrown in the corresponding try block">E e</error>) { }
  }
}