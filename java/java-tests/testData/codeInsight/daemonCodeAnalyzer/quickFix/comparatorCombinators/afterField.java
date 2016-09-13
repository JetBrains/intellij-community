// "Replace with Comparator.comparing" "true"

import java.util.*;

public class Main {
  void sort(List<Person> persons) {
    String p;
    persons.sort(Comparator.comparing(p1 -> p1.name));
  }

  class Person {
    String name;
  }
}
