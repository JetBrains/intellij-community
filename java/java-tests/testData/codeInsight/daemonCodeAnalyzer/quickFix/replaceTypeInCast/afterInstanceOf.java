// "Replace 'LinkedList<String>' with 'ArrayList<String>' in cast" "true-preview"
import java.util.*;

class X {
  void test(List<String> x) {
    if (x instanceof ArrayList) {
      AbstractList<String> list = (ArrayList<String>)x;
    }
  }
}