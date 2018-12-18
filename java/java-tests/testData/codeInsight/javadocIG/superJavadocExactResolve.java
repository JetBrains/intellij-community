public interface Test<E> {

  boolean remove(Object o);

  /** @see TestImpl#remove(int) */
  E remove<caret>(int idx);
}

public class TestImpl<E> implements Test<E> {

  @Override
  public boolean remove(Object o) {
    return false;
  }

  @Override
  public E remove(int idx) {
    return null;
  }
}