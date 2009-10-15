package foo;

class ServiceManager {
  static <T> T getService(Class<T> aClass) {}
}

class MyClass {
  public MyClass getInstance() {
    return ServiceManager.getService(<caret>); 
  }
}