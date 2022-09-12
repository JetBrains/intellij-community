// "Replace with 'String.repeat()'" "true"
class Test {
  String hundredSpaces() {
    String text = "";
      text = text.concat(" ".repeat(100));
    return text;
  }
}