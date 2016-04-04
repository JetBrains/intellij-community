import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

class Main {
  Predicate<String> m() {
    Predicate<Object> qwe = Predicates.alwaysTrue();

    Predicate<String> p1 = Predicates.or(Predicates.alwaysFalse(), Predicates.equalTo("asd"));

    Predicate<String> not = Predicates.not(Predicates.notNull());
    return not;

  }
}