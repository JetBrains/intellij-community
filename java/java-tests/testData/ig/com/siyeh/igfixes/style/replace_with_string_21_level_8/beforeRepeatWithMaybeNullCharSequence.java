// "Replace 'StringBuilder' with 'String'" "true"

class Repeat {
  String foo(CharSequence charSequence) {
    StringBuilder <caret>sb = new StringBuilder();
    sb.repeat(charSequence, 100);
    return sb.toString();
  }
}
