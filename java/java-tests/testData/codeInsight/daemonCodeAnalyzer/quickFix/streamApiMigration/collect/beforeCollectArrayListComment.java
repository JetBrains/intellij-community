// "Replace with collect" "true"
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
      //some comment
      names.add(person.getName());
    }
  }
}
