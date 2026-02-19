// "Replace iteration with bulk 'Collection.addAll()' call" "true-preview"
import java.util.*;

public class Collect {
  class Person {}

  void collectNames(List<Person> persons){
    List<Person> names = new ArrayList<>();
      names.addAll(persons);
  }
}
