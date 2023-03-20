// "Make 'x' effectively final using stream API" "true-preview"
import java.util.*;

class Test {
  public static void main(String[] args) {
    int x = 0;
    for (String arg : args) {
      if (x < arg.length()) {
        x = arg.length();
      }
    }
    Runnable r = () -> System.out.println(<caret>x);
  }
}