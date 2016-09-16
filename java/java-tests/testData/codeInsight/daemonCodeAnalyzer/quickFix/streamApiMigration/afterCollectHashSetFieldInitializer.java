// "Replace with forEach" "true"
import java.util.*;

public class Collect {
  class Person {
    String getName() {
      return "";
    }
  }

  Set<String> names = new HashSet<>();
  void collectNames(List<Person> persons){
      persons.stream().map(Person::getName).forEach(s -> names.add(s));
  }
}
