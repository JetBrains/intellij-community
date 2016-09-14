// "Replace with Comparator.comparingInt" "true"

import java.util.Comparator;
import java.util.List;

public class Main {
  void sort(List<Person> persons) {
    persons.sort(Comparator.comparingInt(p -> p.getName().length()));
  }

  interface Person {
    String getName();
  }
}
