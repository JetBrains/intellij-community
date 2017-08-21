// "Replace with sum()" "true"

import java.util.List;

public class Main {
  interface Person {
    int getAge();
  }

  public long test(List<Person> collection, Person include) {
      long i = collection.stream().filter(include::equals).mapToLong(Person::getAge).sum();
      return i;
  }
}