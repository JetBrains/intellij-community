import java.util.function.Supplier;

class EnumValues {

  {
    Supplier<I<ABC>> supplier = () -> new C<>(ABC::values);
  }

  private static interface I<T> {
    T get();
  }

  private static class C<E> implements I<E> {
    C(Supplier<E[]> supplier) {}

    @Override
    public E get() {
      return null;
    }
  }

  private static enum ABC {
    A, B, C
  }
}