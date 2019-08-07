// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

class Test {

  void statementWithGetGeneratesThrowStatement() {
    Optional.empty<caret>().get();
  }
}