// "Replace iteration with bulk 'List.replaceAll' call" "false"

import java.util.*;
import java.util.function.UnaryOperator;

public class Main {
  static class MyList extends ArrayList<String> {
    @Override
    public void replaceAll(UnaryOperator<String> operator) {
      for (int i = 0; i < super.size(); i++) {
        super.set<caret>(i, operator.apply(super.get(i)));
      }
    }
  }
}