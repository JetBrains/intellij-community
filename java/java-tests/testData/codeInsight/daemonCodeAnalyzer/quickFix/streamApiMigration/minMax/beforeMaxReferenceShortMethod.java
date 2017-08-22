// "Replace with max()" "false"

import java.util.*;

public class Main {
  static class Person {
    String name;
    short age;

    public String getName() {
      return name;
    }

    public short getAge() {
      return age;
    }

    public Person(String name, short age) {
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
      if(p.getAge() > 13) {
        if (p.getAge() > maxPerson.getAge() || maxPerson == null) {
          maxPerson = p;
        }
      }
    }

    return maxPerson;
  }
}