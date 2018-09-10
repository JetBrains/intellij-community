import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

class MyTest {
  {
      Stream<String> stream = Stream.of("a", "b", "c", "d");
      Set<Integer> set = stream.collect(<error descr="No compile-time declaration for the method reference is found">()->new TreeSet<Integer>()</error>, TreeSet::<error descr="Incompatible types: String is not convertible to Integer">add</error>, TreeSet::addAll);
  }
}

abstract class Simplified {
    void m(final BiConsumer<TreeSet<Integer>, TreeSet<Integer>> addAll) {
        Set<Integer> set = collect(TreeSet:: <error descr="Incompatible types: String is not convertible to Integer">add</error>, addAll);
    }

    abstract <R> R collect(BiConsumer<R, String> accumulator, BiConsumer<R, R> combiner);

}