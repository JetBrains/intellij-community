// "Replace with Comparator.comparingDouble" "true"

import java.util.*;

public class Main {
  void sort(List<Person> persons) {
    persons.sort(Comparator.comparingDouble(p -> p.getName().length()));
  }

  interface Person {
    String getName();
  }
}
