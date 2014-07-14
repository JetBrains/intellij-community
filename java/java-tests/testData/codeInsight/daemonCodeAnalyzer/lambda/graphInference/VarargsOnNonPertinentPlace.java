abstract class Im {
  public static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
  public abstract <T> T createProxy(final Class<T> superClass, final Class... otherInterfaces);

  void f(Class<?> implementation, Class rawType,  boolean isInterface) {
    createProxy(implementation,  isInterface ? new Class[]{rawType} : EMPTY_CLASS_ARRAY);
  }
}
