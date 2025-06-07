class Boxed {
  String method(Object foo) {
    if (Boolean.FALSE.equ<caret>als(foo /* hello there! */ instanceof String)) {
      return "foo is String";
    }
    return "foo is not String";
  }
}
