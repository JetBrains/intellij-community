class Sample {
  private <T> void collectClassInformation(java.util.Set<Class<? extends T>> classes) throws Exception {
    for (Class<? extends T> root : classes) {
      collectClassInformation<error descr="'collectClassInformation(java.util.Set<java.lang.Class<? extends T>>)' in 'Sample' cannot be applied to '(java.util.Set<java.lang.Class<? extends capture<? extends T>>>)'">(getSubTypesOf(root))</error>;
    }
  }

  public <T> java.util.Set<Class<? extends T>> getSubTypesOf(Class<T> type) {
    return null;
  }
}