// "Replace 'if else' with '?:'" "INFORMATION"
import java.util.*;

class Test {
  public void multiAssignment(boolean b) {
          List<Integer> l = b ? new ArrayList<Integer>(1) : new ArrayList<Integer>(3);
  }
}
