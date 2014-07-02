import java.util.*;


class Simple {
  public static void main(Set set, Set setO) {
    class O {}

    Map someData = new HashMap();

    set.removeAll(someData.keySet());
    setO.removeAll(someData.keySet());

  }
}