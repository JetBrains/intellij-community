// "Replace with collect" "true"
import java.util.ArrayList;
import java.util.List;

public class Main {
  public List<Runnable> test(List<String> list) {
    List<Runnable> result = new ArrayList<>();
    for(String s : li<caret>st) {
      Runnable r = () -> {
        String str = s;
        if (str.isEmpty()) str = "none";
        System.out.println(str);
      };
      result.add(r);
    }
    return result;
  }
}