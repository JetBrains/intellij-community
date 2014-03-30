// "Replace with collect" "true"
import java.util.*;
import java.util.stream.Collectors;

public class Collect {
  class Person {}

  void collectNames(List<Person> persons){
    List<Person> names = persons.stream().collect(Collectors.toList());
  }
}
