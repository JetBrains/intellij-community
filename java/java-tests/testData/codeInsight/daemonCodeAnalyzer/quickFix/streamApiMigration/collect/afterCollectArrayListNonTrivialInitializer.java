// "Replace with forEach" "true"
import java.util.*;

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
      persons.stream().map(Person::getName).forEach(names::add);
  }
}
