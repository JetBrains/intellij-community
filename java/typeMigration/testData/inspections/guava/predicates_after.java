import java.util.function.Predicate;

public class Main {
  Predicate<String> getPredicate() {
    return input -> true;
  }

  public void m(Predicate<String> p) {
    Predicate<String> orP1 = p.or(p::test).or(getPredicate()::test).or(s -> s.equals("qew"));
    Predicate<String> orP2 = ((Predicate<T>) p::test).or(p).or(getPredicate()::test).or(s -> s.equals("qew"));
    Predicate<String> orP3 = ((Predicate<T>) getPredicate()::test).and(p).and(p::test).and(s -> s.equals("qew"));
    Predicate<String> orP4 = ((Predicate<T>) s -> s.equals("qew")).and(p).and(p::test).and(getPredicate()::test);

    Predicate<String> n1 = ((Predicate<String>) p::test).negate();
    Predicate<String> n2 = p.negate();
    Predicate<Object> n3 = ((Predicate<Object>) s -> s.equals("asd")).negate();
    Predicate<String> n4 = ((Predicate<String>) getPredicate()::test).negate();
  }
}