// "Annotate container as '@NullMarked'|->Annotate class 'Enclosing' as '@NullMarked'" "true"

import org.jspecify.annotations.NullMarked;

class NonNullHolder<T extends @org.jspecify.annotations.NonNull Object> {}

@NullMarked
class Enclosing {
  class Inner {
    public void foo(NonNullHolder<<caret>String> holder) {
    }
  }
}
