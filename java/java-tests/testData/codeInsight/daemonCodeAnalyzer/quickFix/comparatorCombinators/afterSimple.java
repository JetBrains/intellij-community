// "Replace with Comparator.comparing" "true"

import java.util.Comparator;
import java.util.List;

public class Main {
  interface Person {
    String getName();
  }

  void sort(List<Person> persons) {
    persons.sort(Comparator.comparing(Person::getName));
  }
}
