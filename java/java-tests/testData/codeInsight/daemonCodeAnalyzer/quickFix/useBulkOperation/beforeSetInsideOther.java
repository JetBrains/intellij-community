// "Replace iteration with bulk 'List.replaceAll' call" "true"

import java.util.*;
import java.util.function.UnaryOperator;

public class Main {
  static class MyList extends ArrayList<String> {
    public void myReplaceAll(UnaryOperator<String> operator) {
      for (int i = 0; i < super.size(); i++) {
        super.set<caret>(i, operator.apply(super.get(i)));
      }
    }
  }
}