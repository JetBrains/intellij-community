class NestedConditional {

  private String nullIfEmpty(String str) {
    return str == null ? <caret>null : (str.isEmpty() ? null : str);
  }
}