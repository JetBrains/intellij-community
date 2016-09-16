// "Replace with sum()" "true"

import java.util.List;

public class Main {
  interface Person {
    int getAge();
  }

  public long test(List<Person> collection) {
      long i = collection.stream().mapToLong(Person::getAge).sum();
      return i;
  }
}