// "Replace with max()" "true"

import java.util.*;

public class Main {
  static class Person {
    String name;
    int age;

    public String getName() {
      return name;
    }

    public int getAge() {
      return age;
    }

    public Person(String name, int age) {
      this.name = name;
      this.age = age;
    }
  }

  public Person max() {
    List<Person> personList = Arrays.asList(
      new Person("Roman", 21),
      new Person("James", 25),
      new Person("Kelly", 12)
    );
    Person maxPerson = null;
    for <caret>(Person p : personList) {
      if(p.getAge() > /*thirteen*/ 13) {
        if (maxPerson == null || p./*age*/getAge() > maxPerson.getAge()) {
          maxPerson = p; // max!
        }
      }
    }

    return maxPerson;
  }
}