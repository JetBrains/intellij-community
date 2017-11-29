

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Collections.emptyList;

class Example {

  public static <A, B> List<B> map(Function<A, B> fn, List<A> as) {
    return null;
  }

  public static <A, B> B foldLeft(BiFunction<B, A, B> fn, B b, List<A> as) {
    return null;
  }

  public static void main(String[] args) {
    List<CoProduct2<String, Integer>> criteria = emptyList();

    foldLeft((r, t) -> t.into((s, i) -> null),
             new Tuple2<List<Object>, List<Object>>(new ArrayList<>(), new ArrayList<>()),
             map(CoProduct2::project, criteria));
  }

  public static interface CoProduct2<A, B> {
    <R> R match(Function<A, R> aFn, Function<B, R> bFn);

    default Tuple2<Optional<A>, Optional<B>> project() {
      return null;
    }
  }

  public static final class Tuple2<A, B> {
    public Tuple2(A a, B b) {
    }

    public <R> R into(BiFunction<A, B, R> fn) {
      return null;
    }
  }
}

class Example1 {

  private static <Am, Bm> List<Bm> map(Function<Am, Bm> fn, List<Am> as) {
    return null;
  }

  private static <Al> void foldLeft(Consumer<Al> fn, List<Al> as) { }

  public static void foo(final List<CoProduct2<String>> criteria) {
    foldLeft((t) -> t. into(),
             map(CoProduct2::project, criteria));
  }

  public interface CoProduct2<Ax> {
    default Tuple2 project() {
      return null;
    }
  }

  public static final class Tuple2 {
    public void into() { }
  }
}