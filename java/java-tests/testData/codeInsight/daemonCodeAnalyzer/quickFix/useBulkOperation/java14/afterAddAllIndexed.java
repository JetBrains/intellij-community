// "Replace iteration with bulk 'Collection.addAll()' call" "true-preview"
import java.util.*;

public class Collect {
  class Person {}

  void collectNames(Person[] persons){
    List names = new ArrayList();
      names.addAll(Arrays.asList(persons));
  }
}
