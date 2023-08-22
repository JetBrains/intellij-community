class CharPlusAppend {
  int totalLen;

  int prefixLen = 10;
  int suffixLen = 5;
  String s = "samplesamplesamplesamplesamplesamplesamplesamplesamplesamplesamplesamplesamplesample";

  public String testStringBuilder() {
    final StringBuilder s<caret>b = new StringBuilder(totalLen - prefixLen - suffixLen);
    sb.append(Character.toLowerCase(s.charAt(prefixLen)));
    sb.append(s, prefixLen + 1, totalLen - suffixLen);
    return sb.toString();
  }
}