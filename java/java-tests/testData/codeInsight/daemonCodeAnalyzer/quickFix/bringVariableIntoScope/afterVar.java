// "Bring 'String msg' into scope" "true"
public class BringVarIntoScope {
  public void moveVariableDeclarationOutOfBlock(boolean b) {
      String msg;
      if (b) {
          msg = "Leap year";
      } else {
          msg = "Not a leap year";
      }
  }
}