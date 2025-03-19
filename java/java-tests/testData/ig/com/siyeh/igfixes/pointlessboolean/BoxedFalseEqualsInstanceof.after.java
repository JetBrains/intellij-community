class Boxed {
  String method(Object foo) {
    if (!(foo instanceof String)) {
      return "foo is String";
    }
    return "foo is not String";
  }
}
