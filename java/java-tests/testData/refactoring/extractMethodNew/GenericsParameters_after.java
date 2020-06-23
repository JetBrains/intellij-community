class Test {
   public final <T> Class<T> findClass(final String className) throws ClassNotFoundException {
    return (Class<T>)Class.forName(className, true, newMethod());
  }

    private ClassLoader newMethod() {
        return getClass().getClassLoader();
    }
}