// "Replace with 'String.repeat()'" "true"
class Test {
  String hundredSpaces(StringBuilder sb) {
      sb.insert(123, " ".repeat(100));
    return sb.toString();
  }
}