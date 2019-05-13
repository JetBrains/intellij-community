// "Convert to ThreadLocal" "true"
import java.util.Arrays;

class Foo {
  private char[] loo<caret>kahead = new char[0];

  { lookahead = Arrays.copyOf(lookahead, 42);}
}