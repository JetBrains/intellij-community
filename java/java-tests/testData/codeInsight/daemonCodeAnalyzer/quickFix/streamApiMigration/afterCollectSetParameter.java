// "Replace with forEach" "true"
import java.util.*;

public class Collect {
  class Person {
    String getName() {
      return "";
    }
  }

  void collectNames(List<Person> persons, Set<String> names){
      persons.stream().map(Person::getName).forEach(names::add);
  }
}
