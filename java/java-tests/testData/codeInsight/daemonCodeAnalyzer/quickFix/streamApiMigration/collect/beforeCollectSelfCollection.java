// "Replace with forEach" "true"
import java.util.*;

public abstract class Collect implements Collection<String>{
  class Person {
    String getName() {
      return "";
    }
  }

  void collectNames(List<Person> persons){
    for (Person person : pers<caret>ons) {
      add(person.getName());
    }
  }
}
