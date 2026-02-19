import java.util.function.Consumer;
import java.util.function.Function;

public interface Observable<T> {
  void subscribe(Consumer<T> consumer);

  default  <R> Observable<R> map(Function<T, R> f) {
    return cons<caret>umer -> subscribe(null);
  }


}