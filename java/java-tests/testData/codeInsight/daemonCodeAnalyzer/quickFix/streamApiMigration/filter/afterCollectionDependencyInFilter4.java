// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;

class Sample {
  public static void main(List<String> testTags) {
    final List<String> resultJava7 = new ArrayList<>(testTags.size());
      testTags.stream().filter(tag -> !foo(resultJava7)).forEach(tag -> resultJava7.add(tag.trim()));

  }
  
  static boolean foo(List<String> l) {
    return false;
  }
}
