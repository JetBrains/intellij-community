import org.checkerframework.checker.tainting.qual.Untainted;

class CommonCases {
  private @Untainted String sField;

  @Untainted
  private String test(@Untainted String s) {
    String s1 = s + getS(s) + sField;
    return s1;
  }

  private @Untainted String getS(String s) {
    return s;
  }
}