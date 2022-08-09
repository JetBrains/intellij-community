// "Replace with forEach" "true-preview"
import java.util.*;

public class Collect {
  class Person {
    String getName() {
      return "";
    }
  }

  final Set<String> names = new HashSet<>();
  void collectNames(List<Person> persons){
      persons.stream().map(Person::getName).forEach(names::add);
  }
}
