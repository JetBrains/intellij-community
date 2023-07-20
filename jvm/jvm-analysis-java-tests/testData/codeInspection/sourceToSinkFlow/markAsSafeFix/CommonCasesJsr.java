import javax.annotation.Untainted;

class CommonCases {
  private String sField;

  @Untainted
  public String test(String s) {
    String s1 = s + getS(s) + sField;
    return <caret>s1;
  }

  private String getS(String s) {
    return s;
  }
}