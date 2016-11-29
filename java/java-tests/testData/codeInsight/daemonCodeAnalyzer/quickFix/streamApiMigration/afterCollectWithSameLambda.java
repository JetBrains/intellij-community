// "Replace with collect" "true"
import java.util.*;
import java.util.stream.Collectors;

public class Collect {
  public static void collectWithLambda(List<String> test) {
    Runnable r = () -> {
      List<String> result;
      System.out.println("We're inside the lambda");
        result = test.stream().map(String::trim).collect(Collectors.toList());
      System.out.println(result);
    };
    r.run();
  }
}
