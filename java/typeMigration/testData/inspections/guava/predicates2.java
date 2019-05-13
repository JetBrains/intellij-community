import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.sun.org.apache.xpath.internal.operations.String;

class Main {

  void m(Predicate<String> p1, Predicate<String> p2) {
    Predicate<String> megaPredicate = Predicates.or(p1, p2, p1::apply);
    Predicate<String> megaPredicate2 = Predicates.or(p1, p1);

    Predicate<String> megaPredicate3 = Predicates.or(p1);

    Predicate<String> not1 = Predicates.not(p1);

    Predicate<String> not2 = Predicates.not(Predicates.or(p1, Predicates.or(p1, p2)));

  }

  Predicate<String> getPredicate() {
    return new Predicate<String>() {
      @Override
      public boolean apply(String input) {
        return input.equals("asd");
      }
    };
  }

}