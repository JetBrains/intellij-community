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
      names.add(person.getName());
    }
    Collections.// comment
                 sort(names, Comparator.comparing(/*c2*/Person::getName));
  }
}
