// "Replace with forEach" "true"
import java.util.*;

public class Collect {
  class Person {
    String getName() {
      return "";
    }
  }

  void collectNames(List<Person> persons, Set<String> names){
    for (Person person : pers<caret>ons) {
      names.add(person.getName());
    }
  }
}
