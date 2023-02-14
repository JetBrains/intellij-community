// "Add explicit type arguments" "true-preview"
import java.util.Iterator;

public abstract class MyIterable<E> implements Iterable<E> {
  static native <E> MyIterable<E> of(E e);
  final native MyIterable<E> append(E element);
  final native MyIterable<E> append(Iterable<? extends E> other);

  static native <E> MyIterable<E> once(Iterator<? extends E> iterator);

  void split() {
    E next = iterator().next();
    of(next).append(once(foo().takeWhile(e -> e)));
    of(next).append((once(foo().takeWhile(e -><caret> e))));
  }
}