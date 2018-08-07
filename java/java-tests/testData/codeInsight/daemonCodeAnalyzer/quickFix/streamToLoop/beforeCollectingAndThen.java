// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
  void sample(List<String> people) {
    List<String> list1 = people.stream().coll<caret>ect( // comment
                                                         Collectors.collectingAndThen(Collectors.toList(),
                                                                                      Collections::unmodifiableList));
  }

  void sample1(List<String> people) {

    List<String> listIdentity = people.stream().collect( // comment
                                                         Collectors.collectingAndThen(Collectors.toList(),
                                                                                      Function.identity()));
  }

  void sample2(List<String> people) {
    Map<Integer, String> map = people.stream().collect( // comment
                                                        Collectors.collectingAndThen(Collectors.toMap(String::length, Function.identity()),
                                                                                     Collections::unmodifiableMap));
  }

  void sample3(List<String> people) {
    List<String> list2 = people.stream().collect( // comment
      Collectors.collectingAndThen(Collectors.<String, List<String>>toCollection(LinkedList::new),
        list -> Stream.concat(list.stream(), list.stream()).collect(Collectors.toList())));
  }

  void sample4(List<String> people) {
    Map<Integer, List<String>> map = people.stream().collect(
      Collectors.collectingAndThen(Collectors.groupingBy(String::length),
                                   m -> Collections.unmodifiableMap(m)));
  }
}