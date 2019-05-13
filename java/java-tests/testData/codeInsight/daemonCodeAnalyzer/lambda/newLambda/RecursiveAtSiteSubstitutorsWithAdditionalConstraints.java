import java.util.concurrent.ExecutorService;
import java.util.function.*;

abstract class List<A> {

  public abstract int length();
  public abstract <B> B foldLeft(B identity, Function<B, Function<A, B>> f);
  public abstract <B> List<B> map(Function<A, B> f);
  public abstract <B> List<B> flatMap(Function<A, List<B>> f);

  public List<List<A>> splitListAt(int i) {
    return null;
  }

  public List<List<A>> divide(List<List<A>> list, int depth) {
    final List<List<A>> divide = divide(list.flatMap(x -> x.splitListAt(x.length() / 2)), depth / 2);
    return null;
  }

  public <C> void parFoldLeft(ExecutorService es, C identity, Function<C, Function<A, C>> f, List<List<A>> dList) {
    dList.map(x -> es.submit(() -> x.foldLeft(identity, f)));
  }
}
