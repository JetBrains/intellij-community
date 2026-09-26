// "Replace field reference with 'Main.yyy()'" "true-preview"
public class Main {
  public static void main(String[] args) {
    boolean yyy = X<caret>XX;
  }

  /**
   * @deprecated
   * @see Main#yyy()
   */
  static boolean XXX = false;

  static boolean yyy() { return true; }
}
