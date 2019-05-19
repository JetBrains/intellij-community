// "Replace field reference with Main.YYY" "true"
public class Main {
  public static void main(String[] args) {
    boolean yyy = X<caret>XX;
  }

  /**
   * @deprecated
   * @see Main#YYY
   */
  static boolean XXX = false;

  static boolean YYY = true;
}
