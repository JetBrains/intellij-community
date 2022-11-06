// "Make 's' effectively final using stream API" "true-preview"
import java.util.*;

class Test {
  public static void main(String[] args) {
    String s = Arrays.stream(args).filter(arg -> arg.length() > 5).findFirst().orElse(null);
      Runnable r = () -> System.out.println(s);
  }
}