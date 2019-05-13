abstract class Test {

  interface InputFormat<K, V> {
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends InputFormat<?, ?>> getInputFormatClass(final Class<? extends InputFormat> aClass)
    throws ClassNotFoundException {
    return (Class<? extends InputFormat<?, ?>>) aClass;
  }
}
