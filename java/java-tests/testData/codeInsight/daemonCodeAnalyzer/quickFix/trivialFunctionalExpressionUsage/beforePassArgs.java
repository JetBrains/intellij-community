// "Replace method call on method reference with corresponding method call" "true"

import java.util.function.ToIntFunction;

class Test {
  {
    int i = ((ToIntFunction<String>) Integer::parseInt).apply<caret>AsInt("123");
  }
}
