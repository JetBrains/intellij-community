public class splitMutuallyExclusiveIf {
  void foo(Object obj) {
    if (obj instanceof Integer) {
        int x = ((Integer) obj).intValue();
        if (x > 0) {
            System.out.println(x);
        }
    } else if (obj instanceof Float && ((Float) obj).floatValue() > 0.0) {
      System.out.println(((Float) obj).floatValue());
    }
  }
}