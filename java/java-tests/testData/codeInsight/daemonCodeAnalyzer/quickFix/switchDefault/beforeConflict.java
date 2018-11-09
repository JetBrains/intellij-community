// "Unwrap 'switch' statement" "true"
class X {
  String test(char c) {
    s<caret>witch (c) {
      default:
        char a, b = c;
    }
    int b;
  }
}