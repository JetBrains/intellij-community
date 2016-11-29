// "Replace with collect" "false"
import java.util.*;

public class Collect {
  public static void collectWithAnonymous(List<String> test) {
    List<String> result = new ArrayList<>();
    Runnable r = new Runnable() {
      @Override
      public void run() {
        for (String str : te<caret>st) {
          result.add(str.trim());
        }
      }
    };
    r.run();
    System.out.println(result);
  }
}
