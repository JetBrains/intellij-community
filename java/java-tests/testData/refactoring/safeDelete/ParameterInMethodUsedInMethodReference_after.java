interface I {
  boolean m(int i);
}

class A {
  {
    I predicate = (a) -> A.alwaysTrue();
  }

  private static boolean alwaysTrue() {
    return true;
  }
}