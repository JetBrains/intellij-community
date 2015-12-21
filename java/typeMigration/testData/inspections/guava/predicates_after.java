import java.util.function.Predicate;

public class Main {
  Predicate<String> getPredicate() {
    return input -> true;
  }

  public void m(Predicate<String> p) {
    Predicate<String> orP1 = p.or(p).or(getPredicate()).or(s -> s.equals("qew"));
    Predicate<String> orP2 = p.or(p).or(getPredicate()).or(s -> s.equals("qew"));
    Predicate<String> orP3 = getPredicate().and(p).and(p).and(s -> s.equals("qew"));
    Predicate<String> orP4 = ((Predicate<String>) s -> s.equals("qew")).and(p).and(p).and(getPredicate());

    Predicate<String> n1 = p.negate();
    Predicate<String> n2 = p.negate();
    Predicate<Object> n3 = ((Predicate<Object>) s -> s.equals("asd")).negate();
    Predicate<String> n4 = getPredicate().negate();
  }
}