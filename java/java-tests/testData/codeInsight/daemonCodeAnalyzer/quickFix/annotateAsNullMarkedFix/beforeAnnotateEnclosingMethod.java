// "Annotate container as '@NullMarked'|->Annotate method 'foo()' as '@NullMarked'" "true"

class NonNullHolder<T extends @org.jspecify.annotations.NonNull Object> {}

class Enclosing {
  public void foo(NonNullHolder<<caret>String> holder) {
  }
}
