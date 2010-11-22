public class Holder<E> {
  private final E elem;

  private Holder(E elem) { this.elem = elem; }

  public static <<caret>X> Holder<X> of(X elem) { return new Holder<X>(elem); }
}