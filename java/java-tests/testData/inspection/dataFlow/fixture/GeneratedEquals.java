class Bar {
  int foo;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Bar)) return false;

    Bar bar = (Bar) o;

    if (foo != bar.foo) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return foo;
  }
}
