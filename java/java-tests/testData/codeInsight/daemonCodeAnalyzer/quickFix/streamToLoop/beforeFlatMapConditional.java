// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.*;

public class Main {
  public void test(List<List<String>> list) {
    list.stream().flatMap(lst -> lst == null ? Stream.empty() : lst.stream()).<caret>forEach(System.out::println);
  }
}