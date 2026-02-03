class Builders {
  public static A foo() {
    return new A.Builder<>().create();
  }

  static class A<K extends A.Builder<K>> {
    public static class Builder<T extends Builder<T>> {
      public A<T> create() {
        return null;
      }
    }
  }
}


