// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

import java.util.*;

class Test {

  private String returnStatement(String in) {
      if (in != null) return (in);
      return ("foo");
  }

  private void assignment(String in) {
    String out = null;
      out = "foo";
      String value = (in);
      if (value != null) out = value;
  }

  private void declaration(String in) {
      String out = "foo";
      if (in != null) out = in;
  }

  private void statement(String in) {
      if (in != null) System.out.println(in);
  }

  private void statementWithResult(String in) {
      if (in == null) throw new NullPointerException();
  }

  private String partialChain(Optional<String> optional) {
    return optional.orElse("foo");
  }
}