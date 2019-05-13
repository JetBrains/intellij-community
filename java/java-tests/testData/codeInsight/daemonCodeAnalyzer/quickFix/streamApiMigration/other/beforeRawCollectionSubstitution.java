// "Replace with collect" "false"

import java.util.ArrayList;
import java.util.List;

class Main2 {
  public static void main(String[] args) {
    MyList list = new MyList();
    List<Integer> integerList = new ArrayList<>();
    for (Object element : li<caret>st) {
      if (element instanceof Integer) {
        integerList.add((Integer) element);
      }
    }
  }

  public static class MyList extends ArrayList {

  }
}
