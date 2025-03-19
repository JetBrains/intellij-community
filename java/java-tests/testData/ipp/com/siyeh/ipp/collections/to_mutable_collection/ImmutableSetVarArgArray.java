import java.util.*;
import com.google.common.collect.*;

class Test {

  Set<String> test(String[] rest) {
    return ImmutableSet.of<caret>("1", "2", "3", "4", "5", "6", rest);
  }
}