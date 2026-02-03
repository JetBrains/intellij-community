// "Annotate container as '@NullMarked'|->Annotate method 'foo()' as '@NullMarked'" "true"

import org.jspecify.annotations.NullMarked;

class NonNullHolder<T extends @org.jspecify.annotations.NonNull Object> {}

class Enclosing {
  @NullMarked
  public void foo(NonNullHolder<<caret>String> holder) {
  }
}
