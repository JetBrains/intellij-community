// "Migrate to enhanced switch with rules" "false"
public class EmptySwitchExpressions {

  public enum StatusLine {
    A(0), B(1);

    final int status;

    StatusLine(int i) {
      status = i;
    }

    public Integer getStatus() {
      return status;
    }
  }

  public StatusLine myStatusLine;

  public String getTestsStatusColor() {

    return switch<caret> (myStatusLine.getStatus()) {

    };
  }
}
