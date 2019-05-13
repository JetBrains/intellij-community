interface I {
  boolean m(int i);
}

class A {
  {
    I predicate = A::alwaysTrue;
  }

  private static boolean alwaysTrue(int <caret>a) {
    return true;
  }
}