class Test {
  Object m1(String[] args) throws Exception {
    try {
      return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>;
    }
    catch (Throwable t) {
      if (t instanceof Error) {
        System.err.println("1");
      }
      if (!(t instanceof RuntimeException)) {
        System.err.println("2");
      }
      return null;
    }
  }

  void m2(String[] args) throws Exception {
    try {
      System.out.println();
    }
    catch (Throwable t) {
      if (t instanceof Error) {
        System.err.println("1");
      }
      if (!(t instanceof RuntimeException)) {
        System.err.println("2");
      }
    }
  }
}
