import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

class Test {
  final List<String> wordList = new ArrayList<>();

  public void upperCaseWords() {
    wordList.stream().map(String::toUpperCase);

    List<String> output =
      wordList.stream()
        .map(String::toUpperCase)
        .collect(toList());
  }

}
