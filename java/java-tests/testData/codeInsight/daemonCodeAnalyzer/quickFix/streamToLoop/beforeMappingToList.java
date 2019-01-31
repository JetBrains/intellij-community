// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private static void getMap(List<String> strings) {
    System.out.println(strings.stream().co<caret>llect(Collectors.mapping((String::length), Collectors.mapping(((len -> len*2)), Collectors.toList()))));
  }

  public static void main(String[] args) {
    getMap(Arrays.asList("a", "bbb", "cccc", "dddd", "ee", "e"));
  }
}
