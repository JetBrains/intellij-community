

abstract class List<T> {
  private final T head;
  private final List<T> tail;

  protected List() {
    this.head = null;
    this.tail = null;
  }

  private List(T head, List<T> tail) {
    this.head = head;
    this.tail = tail;
  }

  protected T getHead() {
    return head;
  }


  private static class Cons<T> extends List<T> {
    private Cons(T head, List<T> tail) {
      super(head, tail);
    }
  }

  interface Result<K> {
  }

  // The method were the error message is displayed (In class List):
  public static <T, U> Result<List<Tuple<T, U>>> zip(List<T> listT, List<U> listU) {
    List<Tuple<T, U>> list = null;
    List<T> workListT = listT;
    List<U> workListU = listU;
    while (workListT.head != null) {
      list = new Cons<>(new Tuple<>(workListT.getHead(), workListU.getHead()), list);
      workListT = workListT.tail;
      workListU = workListU.tail;
    }
    return null;
  }
}

class Tuple<T, U> {
  public final T _1;
  public final U _2;

  public Tuple(T t, U u) {
    _1 = t;
    _2 = u;
  }
}

