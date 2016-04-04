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
    double value = <error descr="Inconvertible types; cannot cast 'capture<?>' to 'double'">(double) foo.getValue()</error>;
  }
}