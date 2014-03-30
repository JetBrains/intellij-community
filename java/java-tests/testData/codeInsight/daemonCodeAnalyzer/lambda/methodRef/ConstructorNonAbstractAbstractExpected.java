import java.util.ArrayList;
import java.util.List;

class IDEA99970 {
  public static <T, C> Collector<T, C> toCollection(Supplier<C> collectionFactory) {
    return null;
  }

  public static <T> Collector<T, List<T>> toList() {
    return toCollection(ArrayList<T>::new);
  }

  public static <T> Collector<T, List<T>> toList1() {
    return toCollection(ArrayList<T>::new);
  }

}

interface Supplier<T> {
  public T get();
}

interface Collector<T, R> {}