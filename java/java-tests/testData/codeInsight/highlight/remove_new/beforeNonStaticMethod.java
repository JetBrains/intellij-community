// "Remove 'new'" "false"
import java.util.*;

class A {
  boolean x() {
    return new ArrayList.add<caret>("");
  }
}
