// "Replace 'Integer' with 'String' in cast" "true-preview"
import java.util.*;

class X {
  void test() {
    Object x = "foo";
    System.out.println((String)x);
  }
}