public class StringCompareToEqualityFix {

  boolean isSame(String name) {
    return name.compa<caret>reTo("value") == 0;
  }
}
