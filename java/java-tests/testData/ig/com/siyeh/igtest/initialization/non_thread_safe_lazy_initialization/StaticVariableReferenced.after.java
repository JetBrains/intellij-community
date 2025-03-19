public class StaticVariableReferenced {

    private static String s = "yes";

    private static final class ExampleHolder {
        static final Object example = getString(s);
    }

    public static Object getInstance() {
        return ExampleHolder.example;
  }

  private static String getString(String s) {
    return new String(s);
  }
}