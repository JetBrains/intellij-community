class NestedConditional {

  private String nullIfEmpty(String str) {
    return str == null ? null : (str.isEmpty()<caret> ? null : str);
  }
}