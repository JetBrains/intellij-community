// "Extract variable 'trimmed' to 'stream().map' operation" "true-preview"
import java.util.Collection;

public class StreamExtract {
  void hasNonEmpty(Collection<String> list) {
    list.forEach(s -> {
      String <caret>trimmed = s.trim();
      System.out.println(trimmed + ":" + trimmed);
    });
  }
}
