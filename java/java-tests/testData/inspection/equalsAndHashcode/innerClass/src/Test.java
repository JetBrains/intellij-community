class Test {
  class Inner {
    public boolean equals(final Object o) {
      return this == o;
    }
  }

  abstract class A {
    public abstract boolean equals(final Object o);
  }
}
