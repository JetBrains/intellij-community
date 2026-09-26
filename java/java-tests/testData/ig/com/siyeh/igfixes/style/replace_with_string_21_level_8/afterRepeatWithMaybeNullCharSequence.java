// "Replace 'StringBuilder' with 'String'" "true"

class Repeat {
  String foo(CharSequence charSequence) {
      return String.valueOf(charSequence).repeat(100);
  }
}
