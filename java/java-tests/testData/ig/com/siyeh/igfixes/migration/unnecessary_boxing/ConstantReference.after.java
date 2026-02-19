public class ConstantReference {
  private static final boolean DARK = false;
  private static final boolean LIGHT = true;

  private static void configure(boolean isLight) {
    System.out.println(isLight);
  }

  public static void main(String[] args) {
    if (args.length > 0) {
      configure(LIGHT);
    } else {
      configure(DARK);
    }
  }
}