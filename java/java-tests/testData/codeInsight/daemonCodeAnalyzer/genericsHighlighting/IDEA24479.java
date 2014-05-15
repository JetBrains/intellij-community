class SomeUtil {
  public static <T> Iterable<T> iterable(final Iterable<T> enumeration) {
    return null;
  }

  void a(ServletRequest request){
    for (String param : SomeUtil.<String>iterable<error descr="'iterable(java.lang.Iterable<java.lang.String>)' in 'SomeUtil' cannot be applied to '(java.lang.Iterable<java.lang.Object>)'">(request.getParameterNames())</error>) {}
  }

  private class ServletRequest {
    public Iterable<Object> getParameterNames() {
      return null;
    }
  }
}
