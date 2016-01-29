import org.jetbrains.annotations.NotNull;

class Test {
  public static void main(String[] args) {
    Object first = null;
    for (int i = 0; i < 10; i++) {
      if (!"b".equals(first)) {
        first = "b";
      }
      System.out.println(first.toString());
    }
  }

  public void buggyInspectionExample(Object parentNode) {
    final String parentName = parentNode == null ? null : parentNode.toString();
    if ("Topics".equals(parentName)) {
      System.out.println(parentNode.toString());
    } else if ("Queues".equals(parentName)) {
      System.out.println(parentNode.toString());
    }
    System.out.println(parentNode.<warning descr="Method invocation 'toString' may produce 'java.lang.NullPointerException'">toString</warning>());
  }

  public static int foo(String a, String b) {
    if (a.equals(b.startsWith("a") ? b : "")) {
      return 0;
    }
    return a.length();
  }

  static boolean isEmpty(String s) {
    return s.length() == 0;
  }

}