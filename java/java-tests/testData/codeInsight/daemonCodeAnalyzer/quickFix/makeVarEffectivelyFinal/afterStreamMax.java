// "Make 'x' effectively final using stream API" "true-preview"
import java.util.*;

class Test {
  public static void main(String[] args) {
    int x = Arrays.stream(args).mapToInt(String::length).filter(arg -> arg >= 0).max().orElse(0);
      Runnable r = () -> System.out.println(x);
  }
}