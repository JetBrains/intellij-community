interface FunctionalInterface<T> {
  void apply(T t);
}

interface MethodOwner<T> {
  void method(FunctionalInterface<T> fi);
}

class JavaClass {

  void usage(MethodOwner<String> mo) {
    mo.method(<caret>item -> System.out.println(item));
  }
}
