import java.util.Collection;
import java.util.List;

class Testsss {

  public <TA, CA extends Iterable<TA>> void that(Iterable<TA> target) {}

  public <T, C extends Collection<T>> void that(Collection<T> target) {}

  void foo(ImmutableList<String> l) {
    that( l);
  }

  interface ImmutableList<T> extends List<T> {}
}
