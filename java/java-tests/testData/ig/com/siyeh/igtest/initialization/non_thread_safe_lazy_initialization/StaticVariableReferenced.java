public class StaticVariableReferenced {

  private static Object example;
  private static String s = "yes";

  public static Object getInstance() {
    if (example == null) {
      <warning descr="Lazy initialization of 'static' field 'example' is not thread-safe"><caret>example</warning> = getString(s);
    }
    return example;
  }

  private static String getString(String s) {
    return new String(s);
  }
}