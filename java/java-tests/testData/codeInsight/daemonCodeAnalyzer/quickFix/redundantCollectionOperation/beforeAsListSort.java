// "Replace with 'Arrays.sort()'" "true"
import java.util.Arrays;

class Test {
  void test(String[] data) {
    Arrays.as<caret>List(/*array*/data)/*sort*/.sort(/*comparator*/String.CASE_INSENSITIVE_COMPARATOR);
  }
}