final class MyModule {
  @Target({FIELD,PARAMETER,METHOD})
  @Retention(RUNTIME)
  public static @interface Dependency { }
}

final class SomeService {

  SomeService(@My<caret>) {
  }
}