// "Replace with sum()" "true"

import java.util.List;

public class Main {
  interface Person {
    int getAge();
  }

  public long test(List<Person> collection) {
      long i = collection.stream().filter(person -> person != null && person.getAge() >= 10).mapToLong(Person::getAge).sum();
      return i;
  }
}