import javax.annotation.Untainted;

class CommonCases {
    @Untainted
    private String sField;

  @Untainted
  private String test(@Untainted String s) {
    String s1 = s + getS(s) + sField;
    return <caret>s1;
  }

    @Untainted
    private String getS(String s) {
    return s;
  }
}