// "Replace iteration with bulk 'Collection.addAll()' call" "false"
import java.util.*;

public class Collect {
  class Person {}

  void collectNames(Iterable<Person> persons){
    List<Person> names = new ArrayList<>();
    persons.forEach(p -> {
      names<caret>.add(p);
    });
  }
}
