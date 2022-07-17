public class ArrayWriteFlush {
  public static void main(String[] args) {
    int[] a = {0, 0};
    int d = nextInt();
    int x = a[d]++;
    System.out.println(a[0] + a[1]);
    System.out.println(x);
  }

  private static native int nextInt();

  void invalidType() {
    Object obj = 0;
    <error descr="Operator '++' cannot be applied to 'java.lang.Object'">obj++</error>;
    System.out.println(obj);
  }
}