// "Make 'x' effectively final using stream API" "true-preview"
import java.util.*;

class Test {
  public static void main(String[] args) {
    int x = (int) Arrays.stream(args).filter(arg -> !arg.isEmpty()).count();
      Runnable r = () -> System.out.println(x);
  }
}