// "Replace with collect" "true"
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public List<String> test(String[][] arr) {
    List<String> result = Arrays.stream(arr).filter(subArr -> subArr != null).flatMap(Arrays::stream).collect(Collectors.toList());
      return result;
  }
}