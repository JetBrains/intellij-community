interface A<P extends D> {
  void accept(C<P> c);
}

final class AImpl<P extends D> implements A<P> {
  private final B<P> m_b = null;

  @Override
  public final void accept(C<P> c) {
    m_b.accept(c);
  }
}

interface B<P extends D> {
  void accept(C<P> c);
}

interface C<P extends D> {}
interface D {}

