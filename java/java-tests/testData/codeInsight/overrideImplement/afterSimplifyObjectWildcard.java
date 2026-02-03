interface Generic<T> {
  SomeGeneric<? extends T> foo();
}

class II implements Generic<Object> {
    public SomeGeneric<?> foo() {
        <selection>return null;</selection>
    }
}

class SomeGeneric<P> {
}