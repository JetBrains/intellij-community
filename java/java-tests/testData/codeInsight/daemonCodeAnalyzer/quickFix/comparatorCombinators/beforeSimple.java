// "Replace with Comparator.comparing" "true"

import java.util.List;

public class Main {
  interface Person {
    String getName();
  }

  void sort(List<Person> persons) {
    persons.sort((p1, p2) -> p1.getNam<caret>e().compareTo(p2.getName()));
  }
}
