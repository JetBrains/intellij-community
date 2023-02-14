public class splitMutuallyExclusiveIf {
  void foo(Object obj) {
      int i = ((Number) obj).intValue();
      if (obj instanceof Number && i > 0) {
      System.out.println(i);
    } else if (obj instanceof Float && ((Float) obj).floatValue() > 0.0) {
      System.out.println(((Float) obj).floatValue());
    }
  }
}