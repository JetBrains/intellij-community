// "Replace field reference with Main.YYY" "false"
public class Main {
  public static void main(String[] args) {
    boolean yyy = XX<caret>X;
  }

  /**
   * @deprecated
   * @see Main#YYY
   */
  static boolean XXX = false;

  static Object YYY = true;
}
