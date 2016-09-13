// "Replace with Comparator.comparing" "false"

import java.util.List;

public class Main {
  interface Person {
    String getName();
  }

  void sort(List<Person> persons) {
    persons.sort((p1, p2) -> p2.getNam<caret>e().compareTo(p1.getName()));
  }
}
