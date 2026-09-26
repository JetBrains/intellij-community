// "Annotate container as '@NullMarked'|->Annotate class 'Enclosing' as '@NullMarked'" "true"

class NonNullHolder<T extends @org.jspecify.annotations.NonNull Object> {}

class Enclosing {
  @org.jspecify.annotations.NullUnmarked
  class Inner {
    @org.jspecify.annotations.NullUnmarked
    public void foo(NonNullHolder<<caret>String> holder) {
    }
  }
}
