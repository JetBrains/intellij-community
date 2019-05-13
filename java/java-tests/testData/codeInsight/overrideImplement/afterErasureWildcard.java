interface Generic<T> {
  T foo();
}

class II implements Generic<?> {
    public Object foo() {
        <selection>return null;</selection>
    }
}