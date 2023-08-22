// "Bring 'String msg' into scope" "true-preview"
public class BringVarIntoScope {
  public void moveVariableDeclarationOutOfBlock(boolean b) {
    if (b) {
      var msg = "Leap year";
    } else {
      <caret>msg = "Not a leap year";
    }
  }
}