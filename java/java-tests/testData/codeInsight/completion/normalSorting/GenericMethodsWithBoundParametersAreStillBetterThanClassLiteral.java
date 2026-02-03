public class ServiceManager {

  public static <T> T getService(Class<T> serviceClass) {
  }

  public static <T> T getService(Project project, Class<T> serviceClass) {
  }

}


class Foo {
  String getFoo() {
    return ServiceManager.<caret>
  }

}