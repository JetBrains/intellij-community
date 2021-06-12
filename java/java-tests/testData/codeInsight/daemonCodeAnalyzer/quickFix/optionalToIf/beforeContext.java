// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  private String returnStatement(String in) {
    return (Optional.ofNullable<caret>(in).orElse("foo"));
  }

  private void assignment(String in) {
    String out = null;
    out = (Optional.ofNullable((in)).orElse("foo"));
  }

  private void declaration(String in) {
    String out = (Optional.ofNullable(in).orElse("foo"));
  }

  private void statement(String in) {
    (Optional.ofNullable(in).ifPresent(v -> System.out.println(v)));
  }

  private void statementWithResult(String in) {
    (Optional.of(in).orElse("foo"));
  }

  private String partialChain(Optional<String> optional) {
    return optional.orElse("foo");
  }
}