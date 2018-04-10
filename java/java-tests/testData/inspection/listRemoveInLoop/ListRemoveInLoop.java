import java.util.*;

class Test {
  void processList(List<String> list, int start) {
    for(int i=start; i<list.size(); i++) {
      if(list.get(i).isEmpty()) {
        list.<warning descr="Suspicious 'List.remove()' in the loop">remove</warning>(i);
      }
    }
  }

  void processAndCorrect(List<String> list, int start) {
    for(int i=start; i<list.size(); i++) {
      if(list.get(i).isEmpty()) {
        list.remove(i);
        i--;
      }
    }
  }

  void processSingle(List<String> list, int start) {
    for(int i=start; i<list.size(); i++) {
      if(list.get(i).isEmpty()) {
        list.remove(i);
        break;
      }
    }
  }

  void processContinue(List<String> list, int start) {
    for(int i=start; i<list.size(); i++) {
      if(list.get(i).isEmpty()) {
        list.<warning descr="Suspicious 'List.remove()' in the loop">remove</warning>(i);
        continue;
      }
      System.out.println(list.get(i));
    }
  }

  void processContinueOuter(List<String> list, int[] starts) {
    OUTER:
    for(int start : starts) {
      for (int i = start; i < list.size(); i++) {
        if (list.get(i).isEmpty()) {
          list.remove(i);
          continue OUTER;
        }
        System.out.println(list.get(i));
      }
    }
  }

  void deleteTail(List<String> list, int from) {
    for(int i=from; i<list.size(); i++) {
      list.<warning descr="Suspicious 'List.remove()' in the loop">remove</warning>(i);
    }
  }
}