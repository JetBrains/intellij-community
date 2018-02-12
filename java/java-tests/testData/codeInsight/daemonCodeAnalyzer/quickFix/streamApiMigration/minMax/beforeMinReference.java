// "Replace with min()" "true"

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

  public Person work() {
    List<Person> personList = Arrays.asList(
      new Person("Roman", 21),
      new Person("James", 25),
      new Person("Kelly", 12)
    );
    Person minPerson = null;
    for <caret>(Person p : personList) {
      if(p.getAge() > 13) {
        if (minPerson == null || minPerson.getAge() > p.getAge()) {
          minPerson = p;
        }
      }
    }

    return minPerson;
  }
}