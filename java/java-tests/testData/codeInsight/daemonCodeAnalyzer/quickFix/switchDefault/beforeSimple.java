// "Unwrap 'switch'" "true-preview"
class X {
  String test(char c) {
    s<caret>witch (c) {
      default:
        return "foo";
    }
  }
}