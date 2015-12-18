import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

public class Main {
  Predicate<String> getPredicate() {
    return input -> true;
  }

  public void m(Predicate<String> p) {
    Predicate<String> orP1 = Predicates.or(p::apply, p, getPredicate(), s -> s.equals("qew"));
    Predicate<String> orP2 = Predicates.or(p, p::apply, getPredicate(), s -> s.equals("qew"));
    Predicate<String> orP3 = Predicates.and(getPredicate(), p::apply, p, s -> s.equals("qew"));
    Predicate<String> orP4 = Predicates.and(s -> s.equals("qew"), p::apply, p, getPredicate());

    Predicate<String> n1 = Predicates.not(p);
    Predicate<String> n2 = Predicates.not(p::apply);
    Predicate<Object> n3 = Predicates.not(s -> s.equals("asd"));
    Predicate<String> n4 = Predicates.not(getPredicate());
  }
}