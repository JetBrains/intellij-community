// "Annotate container as '@NullMarked'|->Annotate class 'Enclosing' as '@NullMarked'" "true"

class NonNullHolder<T extends @org.jspecify.annotations.NonNull Object> {}

class Enclosing {
  public void foo(NonNullHolder<@org.jetbrains.annotations.Nullable <caret>String> holder) {
  }
}
