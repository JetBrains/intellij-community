import java.util.*;

class Foo<T> {
  final <R> R to(Function<? super Foo<T>, R> converter) {

  }

  static {
    new Foo<Integer>().to(Bar.<Integer>method(<caret>))
  }
}

class Bar {
  static <T> Function<Foo<? extends T>, ?> method(List<?> scope) {
  }

}

interface Function<A,B> {}