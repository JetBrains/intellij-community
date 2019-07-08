/** @noinspection UnusedDeclaration*/
class LimitedPool<T> {
  private int capacity;
  private final ObjectFactory<T> factory;
  private Object[] storage;
  private int index = 0;

  public LimitedPool(final int capacity, ObjectFactory<T> factory) {
    this.capacity = capacity;
    this.factory = factory;
    storage = new Object[capacity];
  }

  interface ObjectFactory<T> {
    T create();
    void cleanup(T t);
  }

  public T alloc() {
    if (index >= capacity) return factory.create();

    if (storage[index] == null) {
      storage[index] = factory.create();
    }

    <error descr="Incompatible types. Found: 'java.lang.Object[]', required: 'T'">return storage;</error>
  }

}