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
      /*age*/
      Person maxPerson = personList.stream().filter(p -> p.getAge() > /*thirteen*/ 13).max(Comparator.comparingInt(Person::getAge)).orElse(null);
      // max!

      return maxPerson;
  }
}