public class splitMutuallyExclusiveIf {
  void foo(Object obj) {
    if (obj instanceof Integer) {
        int i = ((Integer) obj).intValue();
        if (i > 0) {
            System.out.println(i);
        }
    } else if (obj instanceof Float && ((Float) obj).floatValue() > 0.0) {
      System.out.println(((Float) obj).floatValue());
    }
  }
}