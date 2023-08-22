class Foo {
  {
    java.util.function.Function<Object, Object> c = o -> new Object() {
      @Override
      void add {
        System.out.println("hello");
      }
    <caret>};
  }
}
