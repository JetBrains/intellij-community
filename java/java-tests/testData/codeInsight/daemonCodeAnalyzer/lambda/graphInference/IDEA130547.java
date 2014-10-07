import java.util.function.Function;
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
  private static <T> RecCall<Result<T>> firstHelper(final List<T> list, final Function<T, Boolean> f) {
    return cont(new RecCall.Continue<Result<T>>(firstHelper(list.tail, f)));
  }

  public static <T> RecCall<T> cont(final RecCall<T> next) {
    return new RecCall.Continue<T>(next);
  }

}


interface Result<B> {}

interface RecCall<T> {
  public static class Continue<T> implements RecCall<T> {
    private final RecCall<T> next;

    public Continue(RecCall<T> next) {
      this.next = next;
    }
  }
}