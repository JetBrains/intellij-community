
class Test {
  public interface Visitor<Type> {
    boolean visit(Type component);
  }

  public static native void iterate(final Visitor visitor);

  public static void analyze() {
    try {
      iterate((Visitor<String>)(String component) -> true);
      System.out.println("hello");
    }
    catch (Exception e) {
      System.out.println(e);
    }
  }
}