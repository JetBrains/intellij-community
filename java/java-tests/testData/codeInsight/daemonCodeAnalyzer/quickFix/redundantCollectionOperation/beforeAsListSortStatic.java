// "Replace with 'Arrays.sort()'" "true"
import java.util.Arrays;
import java.util.Collections;

class Test {
  void test(String[] data) {
    Collections.sort(/*sort*/Arrays.as<caret>List(/*array*/data)/*no comparator*/);
  }
}