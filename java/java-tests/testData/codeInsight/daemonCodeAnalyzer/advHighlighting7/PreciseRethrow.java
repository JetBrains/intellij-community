class C {
  static class E extends Exception { }
  static class E1 extends E { }
  static class E2 extends E { }
  static class Err extends Error { }

  void m0() {
    try {
      throw new E1();
    }
    catch (Exception e) {
      try {
        // throws E1 in JDK7
        <error descr="Unhandled exception: C.E1">throw e;</error>
      } catch (E2 e2) { }
    }
  }

  void m1() throws E1 {
    try {
      throw new E1();
    }
    catch (Exception e) {
      try {
        // throws E1 in JDK7
        throw e;
      } catch (<error descr="Exception 'C.E2' is never thrown in the corresponding try block">E2 e2</error>) { }
    }
  }

  void m2() {
    try {
      throw new E1();
    }
    catch (Exception e) {
      try {
        if (true) {
          <error descr="Unhandled exception: java.lang.Exception">throw e;</error>
        }
        e = new E1(); // analysis disabled, even by late assignment
      }
      catch (E2 e2) { }
    }
  }

  void m3(boolean f) throws E1, E2 {
    try {
      if (f)
        throw new E1();
      else
        throw new E2();
    }
    catch (Exception e) {
      // read access doesn't disables an analysis
      System.out.println(e);
      // throws E1, E2 in JDK7
      throw e;
    }
  }

  void m4(boolean f) throws E1, E2 {
    try {
      if (f)
        throw new E1();
      else
        throw new E2();
    }
    catch (Exception e) {
      e = new E2(); // analysis disabled
      <error descr="Unhandled exception: java.lang.Exception">throw e;</error>
    }
  }

  void m5(boolean f) throws E {
    try {
      if (f)
        throw new E1();
      else if (!f)
        throw new E2();
      else
        throw (Throwable)new E();
    }
    catch (E1 e1) { }
    catch (final Exception e) {
      // Throwable isn't a subtype of Exception
      <error descr="Unhandled exception: java.lang.Exception">throw e;</error>
    }
    catch (Throwable t) { }
  }

  void m6(boolean f) throws E2 {
    try {
      if (f)
        throw new E1();
      else if (!f)
        throw new E2();
    }
    catch (E1 e1) { }
    catch (final Exception e) {
      throw e;
    }
  }

  void m7() {
    try {
      if (true)
        throw new E1();
      else if (false)
        throw new E2();
    }
    catch (E e) {
      // throws E1, E2 in JDK7
      <error descr="Unhandled exceptions: C.E1, C.E2">throw e;</error>
    }
  }

  void m8() throws E1 {
    try {
      if (true)
        throw new E1();
      else if (false)
        throw new E2();
    }
    catch (E1 | E2 e) {
      <error descr="Unhandled exception: C.E2">throw e;</error>
    }
  }

  void m9() {
    try {
      throw new E1();
    }
    catch (E x) {
      try {
        throw x;
      }
      catch (E y) {
        try {
          throw y;
        }
        catch (E z) {
          // chained exception type evaluation
          <error descr="Unhandled exception: C.E1">throw z;</error>
        }
      }
    }
  }

  void m10() {
    try {
      throw new E1();
    }
    catch (E e) {
      E x = e;
      // no chained exception type evaluation
      <error descr="Unhandled exception: C.E">throw x;</error>
    }
  }

  void m11_1() {
    try {
      System.out.println();
    }
    catch (Exception e) {
      throw e;
    }
  }

  void m11_2() {
    try {
      System.out.println();
    }
    catch (Error e) {
      throw e;
    }
  }

  void m11_3() {
    try {
      System.out.println();
    }
    catch (Err e) {
      throw e;
    }
  }

  void m11_4() {
    try {
      if (false) throw new RuntimeException();
    }
    catch (Exception e) {
      throw e;
    }
  }

  void m11_5() {
    try {}
    catch (Exception e) {
      //parentesis expr
      throw (e);
    }
  }

  static class MyResource implements AutoCloseable {
    public void close() throws E1 { }
  }

  MyResource getResource() throws E2 {
    return null;
  }

  void m12() {
    try (MyResource r = getResource()) {
      System.out.println(r);
    }
    catch (Exception e) {
      // test for another precise types calculation fix
      <error descr="Unhandled exceptions: C.E1, C.E2">throw e;</error>
    }
  }

  void m13() throws E1 {
    try {
      try {
        if (true)
          throw new E1();
        else if (false)
          throw new E2();
      }
      catch (E1 | E2 e) {
        throw e;
      }
    }
    catch (E2 e) {
      throw new RuntimeException(e);
    }
  }

  void m14() {
    try {
      n14(42);
    }
    catch (E1 | E2 e) {
      e.printStackTrace();
      <error descr="Unhandled exceptions: C.E1, C.E2">throw e;</error>
    }
  }

  private void n14(int i) throws E1, E2, RuntimeException {
    if (i == 1) throw new E1();
    if (i == 2) throw new E2();
  }
}