class Foo<T> {

  private T _value;

  T getValue() {
    return _value;
  }

  static Foo<?> getFoo() {
    return new Foo<>();
  }

  public static void main(String[] args) {
    Foo<?> foo = getFoo();
    double value = (double) foo.getValue();
  }
}