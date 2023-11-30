class CharPlusAppend {
  int totalLen;

  int prefixLen = 10;
  int suffixLen = 5;
  String s = "samplesamplesamplesamplesamplesamplesamplesamplesamplesamplesamplesamplesamplesample";

  public String testStringBuilder() {
      String sb = Charact<caret>er.toLowerCase(s.charAt(prefixLen)) +
              s.substring(prefixLen + 1, totalLen - suffixLen);
    return sb;
  }
}