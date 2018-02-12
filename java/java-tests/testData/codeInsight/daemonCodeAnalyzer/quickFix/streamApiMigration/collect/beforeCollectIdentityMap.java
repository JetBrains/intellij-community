// "Replace with collect" "false"
import java.util.*;

public class Collect {
  class Person {}

  void collectNames(List<Person> persons){
    // Handled by UseBulkOperationInspection
    List<Person> names = new ArrayList<>();
    for (Person person : p<caret>ersons) {
      names.add(person);
    }
  }
}
