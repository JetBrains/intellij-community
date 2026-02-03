import java.util.Map;

class MyTest {
  public static void println(Map<String, Object> params) {
    System.out.println(String.format((<warning descr="Casting '\"Some output: %d\"' to 'String' is redundant">String</warning>)"Some output: %d", (Integer)params.get("numberKey")));
  }
}
