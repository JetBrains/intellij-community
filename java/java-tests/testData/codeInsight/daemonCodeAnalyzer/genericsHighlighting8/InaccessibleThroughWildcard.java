class JavacConfiguration {
  private final String mySettings = "";

  public static void foo(Class<? extends JavacConfiguration> aClass) {
    Object o = getService(aClass).<error descr="'mySettings' has private access in 'JavacConfiguration'">mySettings</error>;
    JavacConfiguration configuration = getService(aClass);
    String string = configuration.mySettings;
  }

    public static <T> T getService(Class<T> serviceClass) {
       return null;
     }
}
