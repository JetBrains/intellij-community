class Test {
  public final <T> T createStableValue(Class<?> aClass) {
    return (T) createProxy(aClass.getSuperclass());
  }

  public static <T> T createProxy(final Class<T> superClass) {
    return null;
  }
}