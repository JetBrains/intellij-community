// "Replace with forEach" "true"
import java.util.*;

public class Collect {
  class Person {
    String getName() {
      return "";
    }
  }

  void collectNames(List<Person> persons){
    List<String> names = new ArrayList<>(), otherNames = new ArrayList<>(names);
    for (Person person : pers<caret>ons) {
      names.add(person.getName());
    }
  }
}
