class Foo<T> {
  @<error descr="Annotation type expected">T</error>(value =2) void foo() {}
}