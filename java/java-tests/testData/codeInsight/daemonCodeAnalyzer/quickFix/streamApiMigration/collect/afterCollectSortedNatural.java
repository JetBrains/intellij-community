// "Collapse loop with stream 'collect()'" "true-preview"
import java.util.*;
import java.util.stream.Collectors;

public class Collect {
  void collectNames(List<String> persons){
    List<String> names = persons.stream().map(String::toLowerCase).sorted().collect(Collectors.toList());
      // sort
  }
}
