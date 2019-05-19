// "Replace field reference with Main2.YYY" "true"
public class Main extends Main2 {
  public void main(String[] args) {
    boolean yyy = new Main().YYY;
  }

  /**
   * @deprecated
   * @see Main2#YYY
   */
  boolean XXX = false;
}

class Main2 {
  boolean YYY = true;
}
