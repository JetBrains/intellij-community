// "Replace with collect" "false"
import java.util.*;

public class Collect {
  public static void collectWithLambda(List<String> test) {
    List<String> result = new ArrayList<>();
    Runnable r = () -> {
      for(String str : te<caret>st) {
        result.add(str.trim());
      }
    };
    r.run();
    System.out.println(result);
  }
}
