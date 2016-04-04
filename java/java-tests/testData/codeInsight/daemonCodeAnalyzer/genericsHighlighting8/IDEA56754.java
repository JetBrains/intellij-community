class Foo<T extends Enum> {
  public T bar(Class<? extends T> type, String str) {
    <error descr="Incompatible types. Found: 'java.lang.Enum', required: 'T'">return Enum.valueOf(type, str);</error>
  }
}