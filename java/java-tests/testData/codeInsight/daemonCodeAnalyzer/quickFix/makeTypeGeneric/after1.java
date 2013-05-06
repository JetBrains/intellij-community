// "Change type of TT to java.util.Comparator<java.lang.String>" "true"
import java.util.*;
class CCCC {
  private final static Comparator<String> <caret>TT = new Comparator<String>() {
    @Override
    public int compare(String o1, String o2) {
      return 0;
    }
  };
  }
