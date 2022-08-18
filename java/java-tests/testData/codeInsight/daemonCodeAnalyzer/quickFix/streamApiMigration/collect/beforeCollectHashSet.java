// "Replace with collect" "true-preview"
import java.util.*;

public class Collect {
  class Person {
    String getName() {
      return "";
    }
  }

  void collectNames(List<Person> persons){
    Set<String> names = new HashSet/*valuable comment*/<>();
    for (Person person : pers<caret>ons) {
      names.add(person.getName());
    }
  }
}
