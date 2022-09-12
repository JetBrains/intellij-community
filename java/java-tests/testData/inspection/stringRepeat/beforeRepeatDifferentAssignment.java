// "Replace with 'String.repeat()'" "true"
class Test {
  String tenSpaces() {
    StringBuilder sb = new StringBuilder();
    StringBuilder anotherBuilder = new StringBuilder();
    f<caret>or(int i=0; i<10; i++) {
      anotherBuilder = sb.append(" ");
    }
    return anotherBuilder.toString();
  }
}