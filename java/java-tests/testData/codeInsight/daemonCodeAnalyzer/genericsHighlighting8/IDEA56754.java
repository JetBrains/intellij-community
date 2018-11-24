class Foo<T extends Enum> {
  public T bar(Class<? extends T> type, String str) {
    return <error descr="Incompatible types. Required T but 'valueOf' was inferred to T:
Incompatible types: Enum is not convertible to T">Enum.valueOf(type, str);</error>
  }
}