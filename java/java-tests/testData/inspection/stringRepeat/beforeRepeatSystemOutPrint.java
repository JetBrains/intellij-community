// "Replace with 'String.repeat()'" "true"
class Test {
  void hundredSpaces() {
    f<caret>or(int i=0; i<100; i++) {
      System.out.print(" ");
    }
  }
}