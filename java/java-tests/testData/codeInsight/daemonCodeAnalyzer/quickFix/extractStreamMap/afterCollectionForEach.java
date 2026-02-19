// "Extract variable 'trimmed' to 'stream().map' operation" "true-preview"
import java.util.Collection;

public class StreamExtract {
  void hasNonEmpty(Collection<String> list) {
      list.stream().map(String::trim).forEach(trimmed -> System.out.println(trimmed + ":" + trimmed));
  }
}
