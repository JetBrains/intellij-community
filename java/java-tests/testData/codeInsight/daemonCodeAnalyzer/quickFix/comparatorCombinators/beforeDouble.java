// "Replace with Comparator.comparingDouble" "true"

import java.util.*;

public class Main {
  void sort(List<Person> persons) {
    persons.sort((first, second) -> Double.com<caret>pare(first.getName().length(), second.getName().length()));
  }

  interface Person {
    String getName();
  }
}
