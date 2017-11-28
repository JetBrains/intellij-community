// "Replace with sum()" "true"

import java.util.List;
import java.util.Objects;

public class Main {
  interface Person {
    int getAge();
  }

  public long test(List<Person> collection) {
      long i = collection.stream().filter(Objects::nonNull).mapToLong(Person::getAge).sum();
      return i;
  }
}