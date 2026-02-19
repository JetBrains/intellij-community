// "Replace field reference with 'Main2.yyy()'" "true-preview"
public class Main {
  public static void main(String[] args) {
    boolean yyy = Main2.yyy();
  }

  /**
   * @deprecated
   * @see Main2#yyy()
   */
  static boolean XXX = false;
}

class Main2 {
  static boolean yyy() {return true; }
}
