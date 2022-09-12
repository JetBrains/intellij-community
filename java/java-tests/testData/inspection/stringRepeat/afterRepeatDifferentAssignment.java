// "Replace with 'String.repeat()'" "true"
class Test {
  String tenSpaces() {
    StringBuilder sb = new StringBuilder();
    StringBuilder anotherBuilder = new StringBuilder();
      anotherBuilder = sb.append(" ".repeat(10));
    return anotherBuilder.toString();
  }
}