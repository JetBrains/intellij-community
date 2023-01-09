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
    Set<String> names = new HashSet<>(), other = new HashSet<>();
    if(persons != null) {
        names = persons.stream().map(Person::getName).collect(Collectors.toSet());
    }
    System.out.println(names);
  }
}
