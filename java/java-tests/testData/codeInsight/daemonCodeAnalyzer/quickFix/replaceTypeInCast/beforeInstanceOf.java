// "Replace 'LinkedList<String>' with 'ArrayList<String>' in cast" "true"
import java.util.*;

class X {
  void test(List<String> x) {
    if (x instanceof ArrayList) {
      AbstractList<String> list = (LinkedList<caret><String>)x;
    }
  }
}