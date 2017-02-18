// "Replace with collect" "true"
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public void test(List<String[]> list) {
      List<String> result = list.stream().filter(arr -> arr.length > 2).flatMap(Arrays::stream).map(String::trim).filter(trimmed -> !trimmed.isEmpty()).collect(Collectors.toList());
  }
}