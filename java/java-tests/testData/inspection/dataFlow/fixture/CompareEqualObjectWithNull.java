class Test {
  void test(Object obj) {
    Object base = obj;
    while (base != null) {
      base = getObject(obj);
    }
    // On one hand people write such kind of code (assuming that obj is never null) and the warning looks noise to them
    // On the other hand if precondition loop is used then indeed obj was compared to null, which is useless if we assume that 
    // it's never null. This code is completely equivalent to test2 where the problem is more explicit.
    // If obj is not expected to be null, we should not check the condition before loop, thus do-while should be used
    // (or, alternatively, @NotNull annotation should be added)
    System.out.println(obj.<warning descr="Method invocation 'hashCode' may produce 'NullPointerException'">hashCode</warning>());
  }

  void test2(Object obj) {
    Object base = obj;
    if (base != null) {
      do {
        base = getObject(obj);
      }
      while (base != null);
    }
    System.out.println(obj.<warning descr="Method invocation 'hashCode' may produce 'NullPointerException'">hashCode</warning>());
  }

  void test3(Object obj) {
    Object base = obj;
    do {
      base = getObject(obj);
    }
    while (base != null);
    System.out.println(obj.hashCode());
  }

  native Object getObject(Object obj);
}