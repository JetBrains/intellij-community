// "Replace iteration with bulk 'Collection.addAll()' call" "true-preview"
import java.util.*;

public class Collect {
  class Person {}

  void collectNames(List<Person> persons){
    List<Person> names = new ArrayList<>();
    for(int i = 0; i<persons.size(); i = i + 1) {
      Person p = persons.get(i);
      (names).<caret>add(p);
    }
  }
}
