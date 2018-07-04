class Foo  {
  void processImports(java.util.function.Predicate<String> pr) {}

  {
    processImports(<caret>);
  }

  static <T> T getSomeGenericValue(T t) {}

}