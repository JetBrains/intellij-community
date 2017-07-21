// "Replace with collect" "true"
import java.util.*;
import java.util.stream.Collectors;

public class Collect {
  class Person {
    String getName() {
      return "";
    }
  }

  void collectNames(List<Person> persons){
      List<String> names = persons.stream().map(person -> "name: " + person.getName()).collect(Collectors.toList());
  }
}
