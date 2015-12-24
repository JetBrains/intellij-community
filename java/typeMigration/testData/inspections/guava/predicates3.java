import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

class Main {

  void m(Predicate<String> p1, Predicate<String> p2) {
    Predicate<String> p = Predicates.and(p1, p2);
  }

  Predicate<String> m123(Predicate<String> p1, Predicate<String> p2) {
    return Predicates.and(p1, p2);
  }

  void ml(Predicate<String> p1, Predicate<String> p2) {
    Predicate<String> p = Predicates.and(p1, Predicates.not(p2));
  }

  void mll(Predicate<String> p1, Predicate<String> p2) {
    Predicate<String> p = Predicates.and(p1, Predicates.not(Predicates.or(p2, p1)));
  }

  void mlll(Predicate<String> p1, Predicate<String> p2) {
    FluentIterable<String> fi = FluentIterable.from(new ArrayList<>());
    Iterable<String> ooooooo = fi.filter(Predicates.and(p1, Predicates.not(Predicates.or(p1, p2))));
    Iterable<String> ooooooo1 = fi.filter(p2);
  }
}