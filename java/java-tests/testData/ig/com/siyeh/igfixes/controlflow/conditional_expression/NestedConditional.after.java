class NestedConditional {

  private String nullIfEmpty(String str) {
      if (str == null) {
          return null;
      } else {
          if (str.isEmpty()) return null;
          return str;
      }
  }
}