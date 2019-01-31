// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  void test(List<String> list) {
    List<String> result = list.stream().map(s ->).collect(Collectors.toList());
  }
}