// "Unwrap 'switch'" "true"
class X {
  String test(char c) {
    s<caret>witch (c) {
      default:
        return "foo";
    }
  }
}