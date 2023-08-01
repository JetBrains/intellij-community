public class NestedAssignment {

  private static Object o;

  public static Object getInstance() {
    Object local = null;
    if (o == null) {
      local = <warning descr="Lazy initialization of 'static' field 'o' is not thread-safe"><caret>o</warning> = new Object();
    }
    return o;
  }
}