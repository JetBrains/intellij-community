// "Replace with collect" "true-preview"
import java.util.*;

public class Collect {
  class Person {
    String getName() {
      return "";
    }
  }

  void collectNames(List<Person> persons){
    List<String> names = new ArrayList<>();
    for (Person person : pers<caret>ons) {
      names.add("name: " + person.getName());
    }
  }
}
