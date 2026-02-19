class Negated {

  void check(String contentType) {
    if (!"image/jpeg".equalsIgnoreCase(contentType)) {}
  }
}