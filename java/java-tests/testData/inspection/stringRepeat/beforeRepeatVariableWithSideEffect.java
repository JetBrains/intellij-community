// "Replace with 'String.repeat()'" "false"
class Test {
  String[] hundredSpaces(String[] texts) {
    int j = 123;
    f<caret>or(int i=0; i<100; i++) {
      texts[j++] = texts[j++] + " ";
    }
    return texts;
  }
}