// "Fix all 'Deprecated API usage' problems in file" "false"
public class Main {
  public static void main(String[] args) {
    boolean yyy = X<caret>XX;
  }

  /**
   * @deprecated
   * @see Main#yyy(int)
   */
  static boolean XXX = false;

  static boolean yyy(int y) { return true; }
}
