import java.util.function.Function;

abstract class Result<V> {

 public abstract <U> Result<U> map(Function<V, U> f);

 public abstract <U> Result<U> flatMap(Function<V, Result<U>> f);


 <B, C> void simplified(final Result<Function<B, C>> r, final Result<B> b1) {
   Result<C> rr = r.flatMap(b1::map);
 }


 public static <A, B, C> Function<Result<A>, Function<Result<B>, Result<C>>> lift2(final Function<A, Function<B, C>> f) {
  return a -> b -> a.map(f).flatMap(b::map);
 }

 public static <A, B, C> Function<Result<A>, Function<Result<B>, Result<C>>> lift2a(final Function<A, Function<B, C>> f) {
  return a -> b -> a.map(f).flatMap((Function<B, Result<C>>) (f1) -> b.map<error descr="'map(java.util.function.Function<B,U>)' in 'Result' cannot be applied to '(B)'">(f1)</error>);
 }

 public static <A, B, C> Function<Result<A>, Function<Result<B>, Result<C>>> lift2b(final Function<A, Function<B, C>> f) {
  return a -> b -> a.map(f).flatMap(f1 -> b.map(f1));
 }

 public static <A, B, C> Function<Result<A>, Function<Result<B>, Result<C>>> lift2c(final Function<A, Function<B, C>> f) {
  return a -> b -> a.map(f).flatMap((Function<B, C> f1) -> b.map(f1));
 }
}