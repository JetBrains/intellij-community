public class StringBuilderLengthReturn {
  private static StringBuilder update(StringBuilder sb) {
    sb.append("xyz");
    return sb;
  }

  void test(StringBuilder sb) {
    // No 'always zero' warning
    int diff = sb.length() - update(sb).length();
  }
}