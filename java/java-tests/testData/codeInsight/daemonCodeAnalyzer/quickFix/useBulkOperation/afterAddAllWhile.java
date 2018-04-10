// "Replace iteration with bulk 'Collection.addAll' call" "true"
import java.util.*;

public class Collect {
  static class Person {}

  void collectNames(List<Person> persons, Collection<Person> toAdd){
      persons.addAll(toAdd);
  }
}
