interface Generic<T> {
  T foo();
}

class II implements Generic<?> {
  <caret>
}