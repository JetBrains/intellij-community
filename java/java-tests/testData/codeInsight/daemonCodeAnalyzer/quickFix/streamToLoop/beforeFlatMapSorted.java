// "Replace Stream API chain with loop" "true"

import java.util.*;

public class Main {
  public String testSorted(List<List<String>> list) {
    return list.stream().flatMap(lst -> lst.stream().filter(Objects::nonNull).sorted()).filter(x -> x.length() < 5).<caret>findFirst().orElse("");
  }
}