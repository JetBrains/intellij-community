// "Fix all 'Redundant usage of unmodifiable collection wrappers' problems in file" "false"

import java.util.*;

class Main {

  static List<String> test(List<String> list) {
    return Collections.<caret>unmodifiableList(list);
  }
}
