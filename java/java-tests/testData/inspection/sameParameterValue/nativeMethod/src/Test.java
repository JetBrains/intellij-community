
public class Test {
  static native int getNativeValue(int x);

  public static void main(String[] args) {
    int value = getNativeValue(1);
    System.out.println(value);
  }
}
