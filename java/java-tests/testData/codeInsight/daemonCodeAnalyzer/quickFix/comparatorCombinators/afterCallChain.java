// "Replace with Comparator.comparing" "true"

import java.util.*;

public class Main {
  void sort(List<Person> persons) {
    persons.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ENGLISH)));
  }

  interface Person {
    String getName();
  }
}
