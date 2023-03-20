public class splitMutuallyExclusiveIf {
  void foo(Object obj) {
    if (obj instanceof Integer && ((Integer) obj).intValue() > 0) {
      System.out.println(((Integer) obj).int<caret>Value());
    } else if (obj instanceof Float && ((Float) obj).floatValue() > 0.0) {
      System.out.println(((Float) obj).floatValue());
    }
  }
}