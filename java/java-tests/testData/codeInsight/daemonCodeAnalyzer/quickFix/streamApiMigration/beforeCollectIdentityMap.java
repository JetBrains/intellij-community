// "Replace with collect" "true"
import java.util.*;

public class Collect {
  class Person {}

  void collectNames(List<Person> persons){
    List<Person> names = new ArrayList<>();
    for (Person person : pers<caret>ons) {
      names.add(person);
    }
  }
}
