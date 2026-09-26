final class MyModule {
  @Target({FIELD,PARAMETER,METHOD})
  @Retention(RUNTIME)
  public static @interface Dependency { }
}

class MyAnotherModule {}

final class SomeService {

  SomeService(@My<caret>.Inner) {
  }
}