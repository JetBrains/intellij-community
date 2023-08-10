// "Make 'x' effectively final using stream API" "true-preview"
import java.util.*;

class Test {
  public static void main(String[] args) {
    int x = 0;
    for (String arg : args) {
      x += arg.length();
    }
    Runnable r = () -> System.out.println(<caret>x);
  }
}