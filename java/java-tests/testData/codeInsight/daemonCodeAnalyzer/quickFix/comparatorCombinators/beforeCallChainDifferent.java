// "Replace with Comparator.comparing" "false"

import java.util.*;

public class Main {
  void sort(List<Person> persons) {
    persons.sort((p1, p2) -> p1.getName()<caret>.toLowerCase(Locale.ENGLISH)
      .compareTo(p2.getName().toLowerCase(Locale.CANADA)));
  }

  interface Person {
    String getName();
  }
}
