// "Collapse loop with stream 'collect()'" "true-preview"
import java.util.*;
import java.util.stream.Collectors;

public class Collect {
  class Person {
    String getName() {
      return "";
    }
  }

  void collectNames(List<Person> persons){
    List<String> names = persons.stream().filter(Objects::nonNull).map(Person::getName).collect(Collectors.toList());
  }
}
