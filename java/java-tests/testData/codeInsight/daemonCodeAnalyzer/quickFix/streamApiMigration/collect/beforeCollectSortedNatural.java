// "Replace with collect" "true-preview"
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
