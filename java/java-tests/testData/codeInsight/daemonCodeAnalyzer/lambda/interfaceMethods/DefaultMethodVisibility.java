interface OfInt extends Sink<Integer> {
  default void accept(Integer i) {}
}
interface Sink<T> extends Block<T> {
}

interface Block<T> {
  public void accept(T t);
}


class Hello1 implements Sink<Integer>, OfInt {}
class Hello2 implements OfInt, Sink<Integer>{}

