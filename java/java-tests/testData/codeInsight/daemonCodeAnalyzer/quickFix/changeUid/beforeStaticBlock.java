// "Randomly change 'serialVersionUID' initializer" "false"

import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class Main {
  static final long serialVersionUID<caret>;
  static {
    serialVersionUID = 12L;
  }
}
