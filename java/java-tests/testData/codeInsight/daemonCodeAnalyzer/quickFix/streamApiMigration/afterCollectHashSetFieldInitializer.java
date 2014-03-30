// "Replace with collect" "true"
import java.util.*;
import java.util.stream.Collectors;

public class Collect {
  class Person {
    String getName() {
      return "";
    }
  }

  Set<String> names = new HashSet<>();
  void collectNames(List<Person> persons){
      names.addAll(persons.stream().map(Person::getName).collect(Collectors.toList()));
  }
}
