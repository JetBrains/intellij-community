import java.util.*;
public class Outer {
  private Map<String, String> value = new HashMap<String, String>();

  public class Inner {

    public Inner(String s) {
      if (!value.containsKey(s)) value.put(s, "");
    }
  }
}