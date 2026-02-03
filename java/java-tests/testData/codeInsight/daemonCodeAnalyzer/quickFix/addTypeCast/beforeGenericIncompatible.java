// "Cast expression to 'java.util.List<java.lang.String>'" "false"
import java.util.*;

class a {
  void test() {
    List<String> list = Collections.<caret>singletonList(123);
  }
}

