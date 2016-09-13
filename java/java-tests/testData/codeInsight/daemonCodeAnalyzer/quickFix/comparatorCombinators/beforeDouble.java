// "Replace with Comparator.comparingDouble" "true"

import java.util.*;

public class Main {
  void sort(List<Person> persons) {
    persons.sort((p1, p2) -> Double.com<caret>pare(p1.getName().length(), p2.getName().length()));
  }

  interface Person {
    String getName();
  }
}
