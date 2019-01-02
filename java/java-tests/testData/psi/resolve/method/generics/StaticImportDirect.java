import static java.util.Arrays.asList;
import java.util.*;

class C {
  {
    List<String> s = <caret>asList(new String[0]);
  }
}