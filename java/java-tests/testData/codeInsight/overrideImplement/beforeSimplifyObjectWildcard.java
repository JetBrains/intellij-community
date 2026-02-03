interface Generic<T> {
  SomeGeneric<? extends T> foo();
}

class II implements Generic<Object> {
  <caret>
}

class SomeGeneric<P> {
}