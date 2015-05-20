import java.util.concurrent.Future;

class TypeInferenceBug
{
  public interface Callback<V> {
    void done(V value);
  }

  public static <V> void addCallback(Future<V> future, Callback<? super V> callback) {}

  public static <T> Callback<T> createCallback() {
    return value -> {};
  }

  public static void bind(Future<?> future) {
    addCallback(future, createCallback());
  }
}