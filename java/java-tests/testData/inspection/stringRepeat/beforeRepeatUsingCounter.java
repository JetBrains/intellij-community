// "Replace with 'String.repeat()'" "false"
class Test {
  String[] hundredSpaces(String[] texts) {
    f<caret>or(int i=0; i<100; i++) {
      texts[i] = texts[i] + " ";
    }
    return texts;
  }
}