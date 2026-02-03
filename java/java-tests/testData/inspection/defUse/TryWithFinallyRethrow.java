class C {
  boolean test(boolean b) {
    boolean f = <warning descr="Variable 'f' initializer 'false' is redundant">false</warning>;
    try {
      if (b) throw new RuntimeException();
      f = true;
    }
    catch (RuntimeException e) {
      throw e;
    }
    finally {
    }
    return f;
  }
}