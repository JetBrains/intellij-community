// "Replace iteration with bulk 'Collection.addAll' call" "true"
import java.util.*;

public class Collect {
  static class Person {}

  void collectNames(List<Person> persons, Collection<Person> toAdd){
    Iterator<Person> it = toAdd.iterator();
    while (it.hasNext()) {
      Person person = it.next();
      persons<caret>.add(person);
    }
  }
}
