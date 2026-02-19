// "Annotate container as '@NullMarked'|->Annotate class 'Enclosing' as '@NullMarked'" "true"

class NonNullHolder<T extends @org.jspecify.annotations.NonNull Object> {}

class Enclosing {
  class InnerEnclosing {
    public void foo(NonNullHolder<<caret>String> holder) {
    }
  }
}
