// "Replace 'collect(reducing())' with 'map().reduce()'" "true-preview"

import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public int sum(List<String> data) {
    return data.stream().filter(x -> x.startsWith("xyz")).colle<caret>ct(Collectors.reducing(0, String::length, Integer::sum));
  }
}