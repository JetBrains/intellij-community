// "Collapse loop with stream 'forEach()'" "true-preview"
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
