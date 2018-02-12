public @interface Category {
  Class<?>[] value();
}
@Category(Foo.class<caret>)