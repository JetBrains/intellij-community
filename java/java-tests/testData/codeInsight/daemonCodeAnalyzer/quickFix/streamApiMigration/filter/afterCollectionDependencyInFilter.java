// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.List;

class Sample {
  public static void main(List<String> testTags) {
    final List<String> resultJava7 = new ArrayList<>(testTags.size());
      testTags.stream().filter(tag -> !resultJava7.contains(tag.trim())).forEach(tag -> resultJava7.add(tag.trim()));

  }
}
