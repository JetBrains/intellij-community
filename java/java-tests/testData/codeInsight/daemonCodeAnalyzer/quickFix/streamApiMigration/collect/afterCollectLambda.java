// "Replace with collect" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public List<Runnable> test(List<String> list) {
      List<Runnable> result = list.stream().<Runnable>map(s -> () -> {
          String str = s;
          if (str.isEmpty()) str = "none";
          System.out.println(str);
      }).collect(Collectors.toList());
      return result;
  }
}