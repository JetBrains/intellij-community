// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;

class Sample {
  public static void main(List<String> testTags) {
    final List<String> resultJava7 = new ArrayList<>(testTags.size());
    for (final String tag : tes<caret>tTags) {
      if (!resultJava7.contains(tag.trim())) {
        resultJava7.add(tag.trim());
      }
    }

  }
}
