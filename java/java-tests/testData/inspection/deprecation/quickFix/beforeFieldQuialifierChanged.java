// "Replace field reference with Main2.YYY" "true"
public class Main {
  public static void main(String[] args) {
    boolean yyy = Main.X<caret>XX;
  }

  /**
   * @deprecated
   * @see Main2#YYY
   */
  static boolean XXX = false;
}

class Main2 {
  static boolean YYY = true;
}
