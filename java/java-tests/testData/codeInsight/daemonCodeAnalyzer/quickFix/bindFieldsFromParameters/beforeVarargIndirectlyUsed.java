// "Bind constructor parameters to fields" "false"

import java.util.Collections;
import java.util.LinkedHashSet;

class Person {
  String name;
  int age;

  Person(String name, int age) {
    this.name = name;
    this.age = age;
  }
}

class Interesting extends Person {
  private final String[] hobbies;

  Interesting(String name, int age, String... <caret>hobbies) {
    super(name, age);
    LinkedHashSet hobbySet = new LinkedHashSet<>();
    Collections.addAll(hobbySet, hobbies);
    this.hobbies = hobbySet.toArray(new String[hobbySet.size()]);
  }
}
