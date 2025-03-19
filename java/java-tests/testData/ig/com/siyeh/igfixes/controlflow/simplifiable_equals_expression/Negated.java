class Negated {

  void check(String contentType) {
    if (contentType<caret> == null || !contentType.equalsIgnoreCase("image/jpeg")) {}
  }
}