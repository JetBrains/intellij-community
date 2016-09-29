// "Replace with forEach" "true"
import java.util.*;

public class Collect {
  class Person {
    String getName() {
      return "";
    }
  }

  void collectNames(List<Person> persons){
    Set<String> names = new HashSet<>();
    for(int i=0; i<10; i++) {
      for (Person person : pers<caret>ons) {
        names.add(person.getName());
      }
    }
    System.out.println(names);
  }
}
