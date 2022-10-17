public class patternUsedInSubsequentCondition {
  void x(Object o) {
    if (o instanceof String s && <selection>s.equals("")</selection>) {}
  }
}