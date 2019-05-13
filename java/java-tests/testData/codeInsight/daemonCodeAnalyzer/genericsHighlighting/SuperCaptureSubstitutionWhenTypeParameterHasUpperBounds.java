class Test<T > {
  interface Event{}

  interface EventListener<V extends Event> {
    void handleEvent(V event);
  }

  public void addListener(EventListener<? super T> listener) {
    EventListener<? extends Event> localListener = listener;
    <error descr="Incompatible types. Found: 'Test.EventListener<capture<? super T>>', required: 'Test.EventListener<? super Test.Event>'">EventListener<? super Event>   localListener1 = listener;</error>
  }
}

class Test1 {

  public static class Entity<E extends Entity<E>> {

    public final <T, V extends EntityVisitor<? super E, T>> T handle(final V visitor) {
      return visitor.handle(this);
    }

  }

  public interface EntityVisitor<E extends Entity<E>, T> {

    T handle(Entity<? extends E> e);

  }

}