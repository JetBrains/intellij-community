import java.util.Comparator;

class T {

  {
    new TreeSet<>(Ordering.na<caret>)
  }
}

abstract class Ordering<T> implements Comparator<T> {
  static <C extends Comparable> Ordering<C> natural() {
  }
}

class TreeSet<E> {
  TreeSet(Comparator<? super E> c) {
  }
}
