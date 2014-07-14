import java.util.Set;
import java.util.function.Supplier;

class IDEA122681 {
  private static <E> Set<E> getSet(E element) {
    return null;
  }

  private static <T> T getObject(Supplier<T> supplier) {
    return null;
  }

  private static Object getObjectFromString(String string) {
    return null;
  }

  private static void callGetSet() {
    getSet(getObjectFromString(getObject(String ::new)));
  }

}