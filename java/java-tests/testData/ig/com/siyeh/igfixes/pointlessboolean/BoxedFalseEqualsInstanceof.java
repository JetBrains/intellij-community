class Boxed {
  String method(Object foo) {
    if (Boolean.FALSE.equ<caret>als(foo instanceof String)) {
      return "foo is String";
    }
    return "foo is not String";
  }
}
