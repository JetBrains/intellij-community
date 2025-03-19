import java.util.function.Consumer;
import java.util.function.Function;

public interface Observable<T> {
  void subscribe(Consumer<T> consumer);

  default  <R> Observable<R> map(Function<T, R> f) {
    return new Observable<R>() {
        @Override
        public void subscribe(Consumer<T> consumer) {
            Observable.this.subscribe(null);
        }
    };
  }


}