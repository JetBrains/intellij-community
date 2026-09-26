// "Replace 'StringBuilder' with 'String'" "true"

class Repeat {
  String foo() {
      return String.valueOf((String) null).repeat(100);
  }
}
