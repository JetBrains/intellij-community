class IncompleteSwitch {
  public void testExpression(char o) {
    int i = switch (o) {
      case '2':
        yield 2;
      case char a when a == '1'<EOLError descr="':' or '->' expected"></EOLError>
    };
  }
}
