// "Replace 'collect(summingInt())' with 'mapToInt().sum()'" "true-preview"

import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public void remove(List<Integer> ints, List<String> data) {
    ints.remove((Integer) data.stream().mapToInt(String::length).sum());
  }
}