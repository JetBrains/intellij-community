package reactor.core.publisher;

import java.util.function.Function;

class Mono<T> {
  public final <P> P as(Function<? super Mono<T>,P> transformer) {
    return transformer.apply(this);
  }
}
class Hello {
  public static void main(String[] args) {
    if (<warning descr="Condition 'new Mono<>().as(x -> null) == null' is always 'true'">new Mono<>().as(x -> null) == null</warning>) {}
    if (<warning descr="Condition 'new Mono<>().as(x -> x.toString().trim()) == null' is always 'false'">new Mono<>().as(x -> x.toString().trim()) == null</warning>) {}
  }
}