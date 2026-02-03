// "Change type of TTT to java.util.Comparator" "false"
import java.util.*;
class CCCC implements Comparator {
    private final static Comparator <caret>TTT = new CCCC();
    @Override
    public int compare(Object o1, Object o2) {
      return 0;
    }
  }
