class Foo {
  public static void main(final String[] args) {
    new Bar<RuntimeException>();
  }

  public static class Bar<E extends Throwable> {
    public Bar() throws E {}
  }
}