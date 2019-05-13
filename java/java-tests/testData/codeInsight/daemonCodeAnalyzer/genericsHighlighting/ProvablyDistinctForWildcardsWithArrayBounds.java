class Test {
  public static <T> void fooBar(final Class<?> aClass,
                                final Class<? super Number[]> aSuperClass,
                                final Class<? extends Number[]> anExtendsClass) {
    Class<T[]> klazz = (Class<T[]>) aClass;
               klazz = <error descr="Inconvertible types; cannot cast 'java.lang.Class<capture<? super java.lang.Number[]>>' to 'java.lang.Class<T[]>'">(Class<T[]>) aSuperClass</error>;
               klazz = (Class<T[]>) anExtendsClass;
  }
}