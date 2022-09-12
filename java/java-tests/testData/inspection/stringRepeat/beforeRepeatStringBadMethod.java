// "Replace with 'String.repeat()'" "false"
class Test {
  String hundredSpaces() {
    String text = "";
    f<caret>or(int i=0; i<100; i++) {
      text = text.formatted(" ");
    }
    return text;
  }
}