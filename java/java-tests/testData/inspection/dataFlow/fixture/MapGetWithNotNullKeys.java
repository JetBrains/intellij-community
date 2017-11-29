import foo.*;
import java.util.*;

class Test {

  public void main(Map<@NotNull String, @NotNull Integer> map, HashMap<@NotNull String, @NotNull Integer> hashMap) {
    Integer value = map.get("y");
    if (value == null) {
      System.out.println("it's not contained");
    }

    value = hashMap.get("y");
    if (value == null) {
      System.out.println("it's not contained");
    }
  }
}