class Normal {

  void check(String contentType) {
    if ((contentType != <caret>null) && contentType.equals("image/jpeg")) {}
  }
}