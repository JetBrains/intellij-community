// "Replace iteration with bulk 'List.removeAll' call" "true"
import java.util.*;

public class Collect {
  static class Person {}

  void collectNames(List<Person> persons, Collection<Person> toRemove){
    Iterator<Person> it = toRemove.iterator();
    while (it.hasNext()) {
      Person person = it.next();
      persons<caret>.remove(person);
    }
  }
}
