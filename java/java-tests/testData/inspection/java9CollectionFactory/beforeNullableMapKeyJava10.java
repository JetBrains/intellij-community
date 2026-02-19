// "Replace with 'Map.copyOf()' call" "false"
import org.jetbrains.annotations.*;
import java.util.*;

class Scratch {
  public static void main(HashMap<@Nullable String, String> data) {
    var map = Collections.<caret>unmodifiableMap(new HashMap<>(data));
    System.out.println(map);
  }
}