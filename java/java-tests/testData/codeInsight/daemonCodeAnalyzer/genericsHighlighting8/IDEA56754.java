class Foo<T extends Enum> {
  public T bar(Class<? extends T> type, String str) {
    return Enum.valueOf(<error descr="'valueOf(java.lang.Class<T>, java.lang.String)' in 'java.lang.Enum' cannot be applied to '(java.lang.Class<capture<? extends T>>, java.lang.String)'">type</error>, str);
  }
}