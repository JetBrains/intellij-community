import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Main {

  void m(Predicate<String> p1, Predicate<String> p2) {
    Predicate<String> p = p1.and(p2);
  }

  Predicate<String> m123(Predicate<String> p1, Predicate<String> p2) {
    return p1.and(p2);
  }

  void ml(Predicate<String> p1, Predicate<String> p2) {
    Predicate<String> p = p1.and(p2.negate());
  }

  void mll(Predicate<String> p1, Predicate<String> p2) {
    Predicate<String> p = p1.and(p2.or(p1).negate());
  }

  void mlll(Predicate<String> p1, Predicate<String> p2) {
    Stream<String> fi = new ArrayList<String>().stream();
    Iterable<String> ooooooo = fi.filter(p1.and(p1.or(p2).negate())).collect(Collectors.toList());
    Iterable<String> ooooooo1 = fi.filter(p2).collect(Collectors.toList());
  }
}