class Foo<T extends Enum> {
  public T bar(Class<? extends T> type, String str) {
    return Enum.<error descr="Incompatible types. Found: 'java.lang.Enum', required: 'T'">valueOf</error>(type, str);
  }
}