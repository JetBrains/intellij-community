// "Replace method call on method reference with corresponding method call" "true-preview"

import java.util.function.ToIntFunction;

class Test {
  {
    int i = Integer.parseInt("123");
  }
}
