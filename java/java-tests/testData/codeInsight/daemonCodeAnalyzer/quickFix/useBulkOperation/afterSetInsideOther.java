// "Replace iteration with bulk 'List.replaceAll' call" "true"

import java.util.*;
import java.util.function.UnaryOperator;

public class Main {
  static class MyList extends ArrayList<String> {
    public void myReplaceAll(UnaryOperator<String> operator) {
        super.replaceAll(operator::apply);
    }
  }
}