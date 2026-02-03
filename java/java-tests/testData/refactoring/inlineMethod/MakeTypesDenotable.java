
import java.util.function.Function;

class B<T> {

  void foo(B<? extends CharSequence> sequences){
    sequences.bar(t -> t).ba<caret>z();
  }

  private void baz() {
    T t = null;
  }

  <K> B<? super T> bar(Function<? super T, ? super T> f) {
    return null;
  }
}