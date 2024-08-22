class IncompleteSwitch {


  public void testStatement(char o) {
    switch (o) {
      case
    <error descr="':' or '->' expected"><error descr="Expression, pattern, 'default' or 'null' expected">}</error></error>
    switch (o) {
      case '1'<EOLError descr="':' or '->' expected"></EOLError>
    }
    switch (o) {
      case '1' when<EOLError descr="Expression expected"></EOLError><EOLError descr="':' or '->' expected"></EOLError>
    }
    switch (o) {
      case <error descr="Primitive types in patterns, instanceof and switch are not supported at language level '21'">char a</error> when a == '1'<EOLError descr="':' or '->' expected"></EOLError>
    }


    switch (o) {
      case '2' -> System.out.println("1");
      case
    <error descr="':' or '->' expected"><error descr="Expression, pattern, 'default' or 'null' expected">}</error></error>
    switch (o) {
      case '2' -> System.out.println("1");
      case '1'<EOLError descr="':' or '->' expected"></EOLError>
    }
    switch (o) {
      case '2' -> System.out.println("1");
      case '1' when<EOLError descr="Expression expected"></EOLError><EOLError descr="':' or '->' expected"></EOLError>
    }
    switch (o) {
      case '2' -> System.out.println("1");
      case <error descr="Primitive types in patterns, instanceof and switch are not supported at language level '21'">char a</error> when a == '1'<EOLError descr="':' or '->' expected"></EOLError>
    }
  }

  public void testExpression(char o) {

    int i = switch (<error descr="'switch' expression does not cover all possible input values">o</error>) {
      case '2':
        yield 1;
      case
    <error descr="':' or '->' expected"><error descr="Expression, pattern, 'default' or 'null' expected">}</error></error>;

    i = switch (<error descr="'switch' expression does not cover all possible input values">o</error>) {
      case '2':
        yield 2;
      case '1'<EOLError descr="':' or '->' expected"></EOLError>
    };
    i = switch (<error descr="'switch' expression does not cover all possible input values">o</error>) {
      case '2':
        yield 2;
      case '1' when<EOLError descr="Expression expected"></EOLError><EOLError descr="':' or '->' expected"></EOLError>
    };
    i = switch (o) {
      case '2':
        yield 2;
      case <error descr="Primitive types in patterns, instanceof and switch are not supported at language level '21'">char a</error> when a == '1'<EOLError descr="':' or '->' expected"></EOLError>
    };
  }
}
