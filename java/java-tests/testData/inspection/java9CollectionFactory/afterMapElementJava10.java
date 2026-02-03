// "Replace with 'Map.copyOf()' call" "true"
import org.jetbrains.annotations.*;
import java.util.*;

class Scratch {
  public static void main(HashMap<String, String> data) {
    Map<String, String> map = Map.copyOf(data);
    System.out.println(map);
  }
}