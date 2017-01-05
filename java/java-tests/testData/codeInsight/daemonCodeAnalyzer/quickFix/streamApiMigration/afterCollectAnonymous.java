// "Replace with collect" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public List<Runnable> test(List<String> list) {
      List<Runnable> result = list.stream().map(s -> new Runnable() {
          @Override
          public void run() {
              String str = s;
              if (str.isEmpty()) str = "none";
              System.out.println(str);
          }
      }).collect(Collectors.toList());
      return result;
  }
}