import java.util.*;
import com.google.common.collect.*;

class Test {

  Set<String> test(String[] rest, boolean isVarArg) {
    return isVarArg ? ImmutableSet.<caret>of("1", "2", "3", "4", "5", "6", rest) : Collections.singleton("1");
  }

}