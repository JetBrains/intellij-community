class Foo<T extends Enum> {
  public T <error descr="Invalid return type">bar</error>(Class<? extends T> type, String str) {
    return <error descr="Incompatible types. Required T but 'valueOf' was inferred to T:
Incompatible types: Enum is not convertible to T">Enum.valueOf(type, str);</error>
  }
}