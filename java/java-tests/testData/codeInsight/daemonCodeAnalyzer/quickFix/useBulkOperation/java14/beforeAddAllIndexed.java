// "Replace iteration with bulk 'Collection.addAll()' call" "true"
import java.util.*;

public class Collect {
  class Person {}

  void collectNames(Person[] persons){
    List names = new ArrayList();
    for(int i = 0; i<persons.length; i = i + 1) {
      Person p = persons[i];
      names.<caret>add(p);
    }
  }
}
