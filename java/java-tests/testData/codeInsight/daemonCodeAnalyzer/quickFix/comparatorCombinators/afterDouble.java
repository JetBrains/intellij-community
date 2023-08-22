// "Replace with 'Comparator.comparingDouble'" "true-preview"

import java.util.*;

public class Main {
  void sort(List<Person> persons) {
    persons.sort(Comparator.comparingDouble(person -> person.getName().length()));
  }

  interface Person {
    String getName();
  }
}
