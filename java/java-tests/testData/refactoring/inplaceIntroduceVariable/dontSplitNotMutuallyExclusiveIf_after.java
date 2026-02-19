public class splitMutuallyExclusiveIf {
  void foo(Object obj) {
      int x = ((Number) obj).intValue();
      if (obj instanceof Number && x > 0) {
      System.out.println(x);
    } else if (obj instanceof Float && ((Float) obj).floatValue() > 0.0) {
      System.out.println(((Float) obj).floatValue());
    }
  }
}