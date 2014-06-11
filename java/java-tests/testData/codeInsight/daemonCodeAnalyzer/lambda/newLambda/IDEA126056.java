import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

class StreamTest {
  public class Foo {}

  List<? super Foo> all = new ArrayList<>();

  void foo(final Predicate<Object> predicate, final Stream<? super Foo> stream){
    long the_count= stream.filter((a) -> predicate.test(a)).count();
  }

  void foo1(final Predicate<Object> predicate, final Stream<? extends Foo> stream){
    long the_count= stream.filter((a) -> predicate.test(a)).count();
  }

  void foo2(final Predicate<Object> predicate, final Stream<Foo> stream){
    long the_count= stream.filter((a) -> predicate.test(a)).count();
  }
  
}




