// "Replace with 'String.repeat()'" "true"
class Test {
  String hundredSpaces() {
    String text = "";
    f<caret>or(int i=0; i<100; i++) {
      text += " ";
    }
    return text;
  }
}