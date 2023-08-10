// "Replace with 'Comparator.comparingInt'" "true-preview"

import java.util.List;

public class Main {
  void sort(List<Person> persons) {
    persons.sort((p1, p2) -> {<caret>
      return Integer.compare(p1.getName().length(), p2.getName().length());
    });
  }

  interface Person {
    String getName();
  }
}
