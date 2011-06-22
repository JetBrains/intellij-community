public class MyClass {
  public static void main(String[] args) {
    new Parent(method()) {
    };
  }

  public static String method() {
    return "";
  }

  public static class Parent {
    protected Parent(Object o) {
      System.out.println(o);
    }
  }
}