import java.util.function.Predicate;

public class Main {
  Predicate<String> getPredicate() {
    return input -> true;
  }

  public void m(Predicate<String> p) {
    Predicate<String> orP1 = ((Predicate<String>) p::test).or(p::test).or(getPredicate()::test).or(s -> s.equals("qew"));
    Predicate<String> orP2 = ((Predicate<String>) p::test).or(p::test).or(getPredicate()::test).or(s -> s.equals("qew"));
    Predicate<String> orP3 = ((Predicate<String>) getPredicate()::test).and(p::test).and(p::test).and(s -> s.equals("qew"));
    Predicate<String> orP4 = ((Predicate<String>) s -> s.equals("qew")).and(p::test).and(p::test).and(getPredicate()::test);

    Predicate<String> n1 = ((Predicate<String>) p::test).negate();
    Predicate<String> n2 = ((Predicate<String>) p::test).negate();
    Predicate<Object> n3 = ((Predicate<Object>) s -> s.equals("asd")).negate();
    Predicate<String> n4 = ((Predicate<String>) getPredicate()::test).negate();
  }
}