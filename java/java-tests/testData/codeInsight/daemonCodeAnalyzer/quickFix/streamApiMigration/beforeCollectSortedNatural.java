// "Replace with collect" "true"
import java.util.*;

public class Collect {
  void collectNames(List<String> persons){
    List<String> names = new ArrayList<>();
    for (String person : pers<caret>ons) {
      names.add(person.toLowerCase());
    }
    // sort
    Collections.sort(names);
  }
}
