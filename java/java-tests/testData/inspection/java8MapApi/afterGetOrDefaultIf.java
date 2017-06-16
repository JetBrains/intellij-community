// "Replace with 'getOrDefault' method call" "true"
import java.util.Map;

public class Main {
  private static final String NONE = "none";

  private String str;

  public void testGetOrDefault(Map<String, String> map, String key, Main other) {
      /* output none */
      System.out.println(/* output map value */ map.getOrDefault("k", NONE));
  }
}