import java.util.*;
class A {
      private void foo(Stream<String> a) {
          <selection>a.collect(groupingBy(Function.<String>identity()))</selection>;
      }
  
      interface Collector<T, A, R> {
      }
  
      public static <T, K> Collector<T, ?, Map<K, List<T>>> groupingBy(Function<? super T, ? extends K> classifier) {
          return null;
      }
  
      interface Stream<T> {
          <R, A> R collect(Collector<? super T, A, R> collector);
      }

      interface Function<A, B> {
          B fn(A a);
  
          static <T> Function<T, T> identity() {
            return t -> t;
          }
      }
}