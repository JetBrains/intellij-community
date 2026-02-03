class Test {
  static class ResourcefulException1 extends Exception { }
  static class ResourcefulException2 extends Exception { }
  static class ResourcefulException3 extends Exception { }

  static class ExceptionalResource implements AutoCloseable {
    @Override public void close() throws ResourcefulException1 { }
  }

  ExceptionalResource provideExceptionalResource() throws ResourcefulException2 {
    return new ExceptionalResource();
  }

  void m() {
    try (ExceptionalResource r = provideExceptionalResource()) {
      System.out.println(r);
    }
    catch (Exception e) {
      if (e instanceof ResourcefulException1) {
        System.out.println("1");
      }
      else if (e instanceof ResourcefulException2) {
        System.out.println("2");
      }
      else if (<warning descr="Condition 'e instanceof ResourcefulException3' is always 'false'">e instanceof ResourcefulException3</warning>) {
        System.out.println("3");
      }
    }
  }
}