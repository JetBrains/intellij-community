import foo.*;
import java.util.*;

class Test {

  public void main(Map<@NotNull String, @NotNull Integer> map, HashMap<@NotNull String, @NotNull Integer> hashMap) {
    Integer value = map.get("y");
    if (value == null) {
      System.out.println("it's not contained");
    }
    map.get("z").intValue();

    value = hashMap.get("y");
    if (value == null) {
      System.out.println("it's not contained");
    }
  }

  public void main1(Map<@NotNull String, @Nullable Integer> map, HashMap<@NotNull String, @Nullable Integer> hashMap) {
    map.get("y").<warning descr="Method invocation 'intValue' may produce 'NullPointerException'">intValue</warning>();
    hashMap.get("y").<warning descr="Method invocation 'intValue' may produce 'NullPointerException'">intValue</warning>();
  }

  public void main2(Map<@NotNull String, @NotNull Integer> map, HashMap<@NotNull String, @NotNull Integer> hashMap) {
    Integer value = map.remove("y");
    if (value == null) {
      System.out.println("it's not contained");
    }

    value = hashMap.remove("y");
    if (value == null) {
      System.out.println("it's not contained");
    }
  }
}