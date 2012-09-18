interface Generic<T> {
  SomeGeneric<? extends T> foo();
}

class II implements Generic<Object> {
    public SomeGeneric<?> foo() {
        <selection>return null;  //To change body of implemented methods use File | Settings | File Templates.</selection>
    }
}

class SomeGeneric<P> {
}