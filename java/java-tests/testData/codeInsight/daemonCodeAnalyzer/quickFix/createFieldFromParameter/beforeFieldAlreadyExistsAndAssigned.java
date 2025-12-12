// "Create field for parameter 'hobbies'" "false"

import java.util.Collections;
import java.util.LinkedHashSet;

class Person {
  String name;
  int age;
  private final String[] hobbies;

  Person(String name, int age, String... <caret>hobbies) {
    this.name = name;
    this.age = age;
    LinkedHashSet hobbySet = new LinkedHashSet<>();
    Collections.addAll(hobbySet, hobbies);
    if (hobbies.length == 0) {
      throw new IllegalArgumentException("Interesting person must have at least one hobby.");
    }
    this.hobbies = hobbySet.toArray(new String[hobbySet.size()]);
    System.out.println(hobbies);
  }
}
