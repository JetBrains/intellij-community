interface E {}
interface F extends E {
  ArrayFactory<F> ARRAY_FACTORY = null;
}

interface ArrayFactory<T> {
  T[] create(int count);
}

interface Stub<K> {}

class M {

  public F[] get(Stub s) {
    return foo(s, F.ARRAY_FACTORY);
  }

  private <T extends E> T[] foo(Stub<T> stub, ArrayFactory<T> arrayFactory) {
    return null;
  }
}