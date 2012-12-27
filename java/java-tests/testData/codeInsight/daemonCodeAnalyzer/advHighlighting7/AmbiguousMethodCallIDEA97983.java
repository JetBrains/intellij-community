class Test {
  public static void foo(int[] i1, int[] i2) {
  }

  public static <T> void foo(T i1, T i2) {
  }

  public static final void main(String[] args) throws Exception {
    int[] i = null;
    foo(i, i);
  }
}
class Test1 {
  public static void foo(int i1, int i2) {
  }

  public static <T> void foo(T i1, T i2) {
  }

  public static final void main(String[] args) throws Exception {
    int[] i = null;
    foo(i, i);
  }
}