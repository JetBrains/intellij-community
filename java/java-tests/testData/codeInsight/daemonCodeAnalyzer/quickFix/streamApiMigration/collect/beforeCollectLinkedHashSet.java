// "Collapse loop with stream 'collect()'" "true-preview"
import java.util.*;

public class Collect {
  class Person {
    String getName() {
      return "";
    }
  }

  void collectNames(List<Person> persons){
    Set<String> names = new LinkedHashSet<>();
    for (Person person : pers<caret>ons) {
      names.add(person.getName());
    }
  }
}
