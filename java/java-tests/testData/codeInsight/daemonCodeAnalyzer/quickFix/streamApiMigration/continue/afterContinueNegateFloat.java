// "Replace with sum()" "true"

import java.util.List;

public class Main {
  interface Person {
    double getAge();
  }

  public double test(List<Person> collection) {
      double d = collection.stream().filter(person -> !(person.getAge() == 10)).mapToDouble(Person::getAge).sum();
      return d;
  }
}