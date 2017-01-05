// "Replace with Comparator.comparing" "true"

import java.util.*;

public class Main {
  void sort(List<Person> persons) {
    String p;
    persons.sort((p1, p2) -> p1.name.compar<caret>eTo(p2.name));
  }

  class Person {
    String name;
  }
}
