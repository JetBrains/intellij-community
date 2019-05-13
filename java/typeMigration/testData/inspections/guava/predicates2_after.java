import java.util.function.Predicate;

class Main {

  void m(Predicate<String> p1, Predicate<String> p2) {
    Predicate<String> megaPredicate = p1.or(p2).or(p1);
    Predicate<String> megaPredicate2 = p1.or(p1);

    Predicate<String> megaPredicate3 = p1;

    Predicate<String> not1 = p1.negate();

    Predicate<String> not2 = p1.or(p1.or(p2)).negate();

  }

  Predicate<String> getPredicate() {
    return input -> input.equals("asd");
  }

}