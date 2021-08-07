import java.util.Map;

class MyTest {
  public static void println(Map<String, Object> params) {
    System.out.println(String.format("Some output: %d", (Integer)params.get("numberKey")));
  }
}
