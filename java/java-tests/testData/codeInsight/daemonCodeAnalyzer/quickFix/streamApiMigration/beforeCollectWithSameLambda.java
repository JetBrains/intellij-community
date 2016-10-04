// "Replace with collect" "true"
import java.util.*;

public class Collect {
  public static void collectWithLambda(List<String> test) {
    Runnable r = () -> {
      List<String> result = new ArrayList<>();
      System.out.println("We're inside the lambda");
      for(String str : te<caret>st) {
        result.add(str.trim());
      }
      System.out.println(result);
    };
    r.run();
  }
}
