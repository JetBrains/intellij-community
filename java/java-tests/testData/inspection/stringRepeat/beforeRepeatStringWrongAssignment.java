// "Replace with 'String.repeat()'" "false"
class Test {
  String hundredSpaces() {
    String text = "";
    String anotherText = "";
    f<caret>or(int i=0; i<100; i++) {
      anotherText = text + " ";
    }
    return text;
  }
}