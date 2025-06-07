class Boxed {
  String method(Object foo) {
    if (foo /* hello */ instanceof String) {
      return "foo is String";
    }
    return "foo is not String";
  }
}
