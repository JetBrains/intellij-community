// "Replace with collect" "true"
import java.util.*;
import java.util.stream.Collectors;

public abstract class Collect implements Collection<String>{
  class Person {
    String getName() {
      return "";
    }
  }

  void collectNames(List<Person> persons){
      addAll(persons.stream().map(Person::getName).collect(Collectors.toList()));
  }
}
