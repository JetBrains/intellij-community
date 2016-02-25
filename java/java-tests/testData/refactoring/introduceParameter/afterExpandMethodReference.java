interface I {
  boolean m(int a);
}

class A {
  {
    I predicate = (a) -> alwaysTrue(a, "");
  }

  private static boolean alwaysTrue(int a, String anObject) {
      return true;
  }
}