class Foo<T extends Enum> {
  public T bar(Class<? extends T> type, String str) {
    return <error descr="Incompatible types. Found: 'java.lang.Enum', required: 'T'">Enum.valueOf(type, str);</error>
  }
}