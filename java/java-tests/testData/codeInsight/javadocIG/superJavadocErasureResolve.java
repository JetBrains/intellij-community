public interface Test<E extends Number> {

  boolean remove(E e);

  /**
   * @see TestImpl#remove(Number)
   */
  E remove<caret>(Integer idx);
}

public class TestImpl<E extends Number> implements Test<E> {

  @Override
  public boolean remove(E e) {
    return false;
  }

  @Override
  public E remove(Integer idx) {
    return null;
  }
}