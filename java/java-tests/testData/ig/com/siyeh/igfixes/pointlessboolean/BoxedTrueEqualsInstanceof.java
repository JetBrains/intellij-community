class Boxed {
  String method(Object foo) {
    if (Boolean.TRUE.equ<caret>als(foo /* hello */ instanceof String)) {
      return "foo is String";
    }
    return "foo is not String";
  }
}
