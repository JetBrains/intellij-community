interface I {
  boolean m(int a);
}

class A {
  {
    I predicate = A::alwaysTrue;
  }

  private static boolean alwaysTrue(int a) {
    <selection>""</selection>
    return true;
  }
}