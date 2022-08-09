// "Replace with collect" "true-preview"
import java.util.*;

public class Collect {
  class Person {
    String getName() {
      return "";
    }
  }

  void collectNames(List<Person> persons){
    Set<String> names = new HashSet<>(), other = new HashSet<>();
    if(persons != null) {
      for (Person person : pers<caret>ons) {
        names.add(person.getName());
      }
    }
    System.out.println(names);
  }
}
