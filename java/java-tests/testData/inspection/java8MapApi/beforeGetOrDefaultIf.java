// "Replace with 'getOrDefault' method call" "true"
import java.util.Map;

public class Main {
  private static final String NONE = "none";

  private String str;

  public void testGetOrDefault(Map<String, String> map, String key, Main other) {
    if(map.conta<caret>insKey("k")) {
      System.out.println(/* output map value */ map.get("k"));
    } else {
      System.out.println(/* output none */ NONE);
    }
  }
}