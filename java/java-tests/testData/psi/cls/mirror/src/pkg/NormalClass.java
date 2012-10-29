package pkg;

class NormalClass {
  private final Object o = new Inner();

  Object get() {
    return o;
  }

  private static class Inner { }
}