// "Replace iteration with bulk 'List.removeAll' call" "true"
import java.util.*;

public class Collect {
  static class Person {}

  void collectNames(List<Person> persons, Collection<Person> toRemove){
    for (Iterator<Person> it = toRemove.iterator(); it.hasNext(); )
      persons<caret>.remove(it.next());
  }
}
