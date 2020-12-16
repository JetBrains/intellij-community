import java.lang.reflect.Method;

// IDEA-257674
class ABC {
  public static void method(String arg, int num) {
    System.out.println(arg + " " + num);
  }

  public static void main(String[] args) throws Exception {
    Method m = ABC.class.getMethod("method", String.class, int.class);
    Object o = new Object[] {"123", 124};
    m.invoke(null, (Object[]) o);
  }
}