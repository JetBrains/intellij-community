// "Replace with collect" "true"
import java.util.*;
import java.util.stream.Collectors;

public class Collect {
  class Person {
    String getName() {
      return "";
    }
  }

  ArrayList<String> foo() {
    return new ArrayList<>();
  }

  void collectNames(List<Person> persons){
    List<String> names = foo();
      names.addAll(persons.stream().map(Person::getName).collect(Collectors.toList()));
  }
}
