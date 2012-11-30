interface Generic<T> {
  T foo();
}

class II implements Generic<?> {
    public Object foo() {
        <selection>return null;  //To change body of implemented methods use File | Settings | File Templates.</selection>
    }
}