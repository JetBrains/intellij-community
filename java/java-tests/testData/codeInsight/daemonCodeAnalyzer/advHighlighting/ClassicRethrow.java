class C {
  static class E extends Exception { }
  static class E1 extends E { }
  static class E2 extends E { }
  static class Err extends Error { }

  void m1() {
    try {
      throw new E1();
    }
    catch (Exception e) {
      try {
        // throws Exception before JDK7
        <error descr="Unhandled exception: java.lang.Exception">throw e;</error>
      }
      catch (E2 e2) { }
    }
  }

  void m2() {
    try {
      throw new E1();
    }
    catch (Exception e) {
      try {
        e = new E1(); // no effect before JDK7
        <error descr="Unhandled exception: java.lang.Exception">throw e;</error>
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
      // throws Exception before JDK7
      <error descr="Unhandled exception: java.lang.Exception">throw e;</error>
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
      e = new E2(); // no effect before JDK7
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
      // throws Exception
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
      // throws Exception before JDK7
      <error descr="Unhandled exception: java.lang.Exception">throw e;</error>
    }
  }

  void m7() {
    try {
      throw new E1();
    }
    catch (E e) {
      try {
        throw e;
      }
      catch (E x) {
        // no chained exception type evaluation
        <error descr="Unhandled exception: C.E">throw x;</error>
      }
    }
  }

  void m8() {
    try {
      throw new E1();
    }
    catch (E e) {
      E x = e;
      // no chained exception type evaluation
      <error descr="Unhandled exception: C.E">throw x;</error>
    }
  }

  void m9_1() {
    try {
      System.out.println();
    }
    catch (Throwable t) {
      // throws Throwable before JDK7
      <error descr="Unhandled exception: java.lang.Throwable">throw t;</error>
    }
  }

  void m9_2() {
    try {
      System.out.println();
    }
    catch (Error e) {
      throw e;
    }
  }

  void m9_3() {
    try {
      System.out.println();
    }
    catch (Err e) {
      throw e;
    }
  }

  void m9_4() {
    try {
      if (false) throw new RuntimeException();
    }
    catch (Exception e) {
      // throws Exception before JDK7
      <error descr="Unhandled exception: java.lang.Exception">throw e;</error>
    }
  }
}