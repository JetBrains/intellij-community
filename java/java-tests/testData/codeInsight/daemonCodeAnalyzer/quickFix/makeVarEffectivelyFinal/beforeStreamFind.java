// "Make 's' effectively final using stream API" "true-preview"
import java.util.*;

class Test {
  public static void main(String[] args) {
    String s = null;
    for (String arg : args) {
      if (arg.length() > 5) {
        s = arg;
        break;
      }
    }
    Runnable r = () -> System.out.println(<caret>s);
  }
}