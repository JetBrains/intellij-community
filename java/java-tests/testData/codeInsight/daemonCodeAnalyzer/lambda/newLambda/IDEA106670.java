import java.util.*;

class Test {
  interface BiFunction<T, U, R> {
    R apply(T t, U u);
  }

  interface Stream<SSS> {
  }

  public void test(Stream<String> range1, Stream<String> range2) {
    zip(range1, range2, (f, s) -> asList(f, s));
    zip(range1, range2, Test::asList);
    
    BiFunction<? super String, ? super String, ?> asList = Test::asList;
    zip(range1, range2, asList);
  }

  public static <T> List<T> asList(T... a) {
    return null;
  }

  
  public static <A, B, C> Stream<C> zip(Stream<? extends A> a,
                                        Stream<? extends B> b,
                                        BiFunction<? super A, ? super B, ? extends C> zipper) {
    return null;
  }
}

class Test1111 {
  interface I<R> {
    R apply();
  }

  public void test(I<?> i) {
      bar(i);
  }

  public static <A> void bar(I<? super A> i) {}
}

