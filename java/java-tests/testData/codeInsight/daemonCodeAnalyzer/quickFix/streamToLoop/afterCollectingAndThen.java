// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
  void sample(List<String> people) {
      // comment
      List<String> list = new ArrayList<>();
      for (String person : people) {
          list.add(person);
      }
      List<String> list1 = Collections.unmodifiableList(list);
  }

  void sample1(List<String> people) {

      // comment
      List<String> list = new ArrayList<>();
      for (String person : people) {
          list.add(person);
      }
      List<String> listIdentity = list;
  }

  void sample2(List<String> people) {
      // comment
      Map<Integer, String> result = new HashMap<>();
      for (String person : people) {
          if (result.put(person.length(), person) != null) {
              throw new IllegalStateException("Duplicate key");
          }
      }
      Map<Integer, String> map = Collections.unmodifiableMap(result);
  }

  void sample3(List<String> people) {
    List<String> list2 = people.stream().collect( // comment
      Collectors.collectingAndThen(Collectors.<String, List<String>>toCollection(LinkedList::new),
        list -> {
            List<String> result = new ArrayList<>();
            for (Iterator<String> it = Stream.concat(list.stream(), list.stream()).iterator(); it.hasNext(); ) {
                String s = it.next();
                result.add(s);
            }
            return result;
        }));
  }
}