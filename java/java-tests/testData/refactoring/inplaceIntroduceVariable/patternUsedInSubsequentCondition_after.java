public class patternUsedInSubsequentCondition {
  void x(Object o) {
    if (o instanceof String s) {
        boolean equals = s.equals("");
        if (equals) {
        }
    }
  }
}