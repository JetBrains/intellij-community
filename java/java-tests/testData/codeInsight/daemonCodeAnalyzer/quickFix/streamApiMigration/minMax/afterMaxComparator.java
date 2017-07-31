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

  public Person work() {
    List<Person> personList = Arrays.asList(
      new Person("Roman", 21),
      new Person("James", 25),
      new Person("Kelly", 12)
    );
    Person maxPerson;
    Comparator<Person> personComparator = Comparator.comparingInt(Person::getAge);
      maxPerson = personList.stream().filter(p -> p.getAge() > 13).max(personComparator).orElse(null);

    return maxPerson;
  }
}