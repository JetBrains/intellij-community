// "Replace with 'String.repeat()'" "true"
class Test {
  String hundredSpaces() {
    String text = "";
      text = " ".repeat(100).concat(text);
    return text;
  }
}