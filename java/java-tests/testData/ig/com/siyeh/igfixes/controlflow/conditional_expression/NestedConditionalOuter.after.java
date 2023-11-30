class NestedConditional {

  private String nullIfEmpty(String str) {
      if (str == null) return null;
      return str.isEmpty() ? null : str;
  }
}