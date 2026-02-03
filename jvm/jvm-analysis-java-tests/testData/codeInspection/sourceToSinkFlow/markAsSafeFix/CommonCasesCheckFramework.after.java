import org.checkerframework.checker.tainting.qual.Untainted;

class CommonCases {
  public @Untainted String sField;

  @Untainted
  public String test(@Untainted String s) {
    String s1 = s + getS(s) + sField;
    return s1;
  }

  private @Untainted String getS(String s) {
    return s;
  }
}