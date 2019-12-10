// "Replace 'Integer' with 'String' in cast" "true"
import java.util.*;

class X {
  void test() {
    Object x = "foo";
    System.out.println((<caret>Integer)x);
  }
}