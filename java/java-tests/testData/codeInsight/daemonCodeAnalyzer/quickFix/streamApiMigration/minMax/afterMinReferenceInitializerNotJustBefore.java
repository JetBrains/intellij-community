// "Replace with min()" "true"

import java.util.*;

class Main {
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
    Person minPerson;
    List<Person> personList = Arrays.asList(
      new Person("Roman", 21),
      new Person("James", 25),
      new Person("Kelly", 12)
    );
      minPerson = personList.stream().filter(p -> p.getAge() > 13).min(Comparator.comparingInt(Person::getAge)).orElse(null);
  }
}