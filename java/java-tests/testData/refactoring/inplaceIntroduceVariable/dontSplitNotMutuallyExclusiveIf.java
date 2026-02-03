public class splitMutuallyExclusiveIf {
  void foo(Object obj) {
    if (obj instanceof Number && ((Number) obj).intValue() > 0) {
      System.out.println(((Number) obj).int<caret>Value());
    } else if (obj instanceof Float && ((Float) obj).floatValue() > 0.0) {
      System.out.println(((Float) obj).floatValue());
    }
  }
}