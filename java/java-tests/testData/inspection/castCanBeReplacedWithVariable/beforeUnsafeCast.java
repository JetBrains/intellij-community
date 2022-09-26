// "Replace '(ArrayList<Integer>) obj' with 'arrayList'" "false"

import java.util.*;

class X {
  void test(List obj) {
    ArrayList arrayList = (ArrayList) obj;
    Integer list = ((ArrayList<Integer>) ob<caret>j).get(0);
  }
}