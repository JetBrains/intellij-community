class IncompleteSwitch {
  public void testExpression(char o) {
    int i = switch (o) {
      case '2':
        yield 2;
      case <error descr="Primitive types in patterns, instanceof and switch are not supported at language level '21'">char a</error> when a == '1'<EOLError descr="':' or '->' expected"></EOLError>
    };
  }
}
