// "Replace with findFirst()" "true"

import java.util.List;

class Scratch {
  public void setStatus(List<MutablePair> destinations, String destination, Integer status) {
    fo<caret>r (MutablePair pair : destinations) {
      if (pair.first.compareTo(destination) == 0) {
        pair.second = status;
        return;
      }
    }
  }

  private static class MutablePair {
    String first;
    Integer second;
  }
}