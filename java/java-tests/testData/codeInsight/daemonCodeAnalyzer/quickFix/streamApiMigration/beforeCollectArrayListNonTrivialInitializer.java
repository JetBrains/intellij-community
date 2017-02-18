// "Replace with forEach" "true"
import java.util.*;

public class Collect {
  class Person {
    String getName() {
      return "";
    }
  }

  ArrayList<String> foo() {
    return new ArrayList<>();
  }

  void collectNames(List<Person> persons){
    List<String> names = foo();
    for (Person person : pers<caret>ons) {
      names.add(person.getName());
    }
  }
}
