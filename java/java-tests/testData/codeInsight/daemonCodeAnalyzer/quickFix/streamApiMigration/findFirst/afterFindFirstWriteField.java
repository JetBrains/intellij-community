// "Replace with findFirst()" "true"

import java.util.List;

class Scratch {
  public void setStatus(List<MutablePair> destinations, String destination, Integer status) {
      destinations.stream().filter(pair -> pair.first.compareTo(destination) == 0).findFirst().ifPresent(pair -> pair.second = status);
  }

  private static class MutablePair {
    String first;
    Integer second;
  }
}